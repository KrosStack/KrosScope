package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.writeFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchGitStateTest {
    private val markers = listOf(
        "index.lock",
        "rebase-merge",
        "rebase-apply",
        "MERGE_HEAD",
        "CHERRY_PICK_HEAD",
        "REVERT_HEAD",
        "BISECT_LOG",
    )

    @Test
    fun `each git marker pauses updates then the quiet period releases one`() = inWorkspace { root ->
        git(root, "init", "-q")
        val view = WatchGitState.open(root)
        for (marker in markers) {
            val clock = WatchTestClock()
            val state = WatchState(WatchSettings(), { clock.nanos }, clock::stamp, view)
            state.scanStarted()
            state.scanCompleted(false)
            val path = root.resolve(".git/$marker")
            Files.createFile(path)
            clock.change()
            state.poll()
            clock.at(10)
            state.poll()
            assertFalse(state.beginUpdate(), marker)
            assertEquals(WatchReason.GIT, state.reason(), marker)
            Files.delete(path)
            state.poll()
            assertFalse(state.beginUpdate(), marker)
            clock.at(14)
            state.poll()
            assertTrue(state.beginUpdate(), marker)
            state.updateCompleted(0, false)
            assertFalse(state.beginUpdate(), marker)
        }
    }

    @Test
    fun `an operation in progress in any git directory pauses updates`() = inWorkspace { root ->
        val directories = listOf("first", "second").map { Files.createDirectory(root.resolve(it)) }
        val view = WatchGitState.fromDirectories { directories }
        assertFalse(view())
        Files.createFile(directories.last().resolve("index.lock"))
        assertTrue(view())
    }

    @Test
    fun `linked worktrees use their own git directory`() = inWorkspace { root ->
        val repository = Files.createDirectory(root.resolve("repository"))
        git(repository, "init", "-q")
        writeFile(repository, "source.kt", "class Source")
        git(repository, "add", ".")
        git(repository, "commit", "-qm", "sample")
        val linked = root.resolve("linked")
        git(repository, "worktree", "add", "-qb", "linked", linked.toString())
        val ownDirectory = Path.of(git(linked, "rev-parse", "--absolute-git-dir"))
        val view = WatchGitState.open(linked)
        Files.createFile(repository.resolve(".git/index.lock"))
        assertFalse(view())
        Files.createFile(ownDirectory.resolve("index.lock"))
        assertTrue(view())
        Files.delete(ownDirectory.resolve("index.lock"))
        assertFalse(view())
    }

    @Test
    fun `a commit that changes no file leaves the watcher behind until the next scan`() = inWorkspace { root ->
        git(root, "init", "-q")
        git(root, "add", "-A")
        git(root, "commit", "-qm", "first")
        ServeWatch(root, ServeSettings(watchEnabled = true)).use { watch ->
            writeFile(root, "lib/src/main/kotlin/Lib.kt", "class Lib\nfun edited() = 1\n")
            git(root, "add", "-A")
            watch.state.scanStarted()
            watch.state.scanCompleted(false)
            watch.state.poll()
            assertNull(watch.state.reason())
            git(root, "commit", "-qm", "second")
            watch.state.poll()
            assertNotNull(watch.state.reason())
            Unit
        }
    }

    @Test
    fun `a workspace outside git has no pause`() = inWorkspace { root ->
        assertNeverPaused(WatchGitState.open(root))
    }

    @Test
    fun `a missing git executable has no pause or polling failures`() = inWorkspace { root ->
        assertNeverPaused(WatchGitState.open(root, "kscope-no-such-executable"))
    }

    private fun assertNeverPaused(view: () -> Boolean) {
        val clock = WatchTestClock()
        val state = WatchState(WatchSettings(), { clock.nanos }, clock::stamp, view)
        state.scanStarted()
        state.scanCompleted(false)
        clock.change()
        for (second in listOf(0L, 10L, 30L, 70L, 150L)) {
            assertFalse(view())
            clock.at(second)
            state.poll()
            assertEquals(WatchReason.SETTLING, state.reason())
            assertFalse(state.stopped)
        }
        assertTrue(state.beginUpdate())
    }

    private fun git(root: Path, vararg arguments: String): String {
        val process = ProcessBuilder(
            listOf("git", "-c", "user.name=Watch Test", "-c", "user.email=watch@example.invalid") + arguments,
        ).directory(root.toFile()).redirectErrorStream(true).start()
        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
        val text = process.inputStream.bufferedReader().readText().trim()
        assertEquals(0, process.exitValue(), text)
        return text
    }
}
