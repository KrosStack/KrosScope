package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.RegisteredRepository
import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.structure.TestGit
import io.github.krosstack.kscope.engine.workspaceState
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** A Git repository at [directory] with one file, committed unless [commit] is false; returns its normal path. */
internal fun repositoryAt(directory: Path, commit: Boolean = true): Path {
    Files.createDirectories(directory)
    Files.writeString(directory.resolve("README.md"), "${directory.fileName}\n")
    TestGit.output(directory, "init", "-q")
    if (commit) {
        TestGit.output(directory, "add", "-A")
        TestGit.output(directory, "commit", "-q", "-m", "initial")
    }
    return directory.toAbsolutePath().normalize()
}

private fun layout(workspace: TestWorkspace, vararg registered: RegisteredRepository) =
    WorkspaceRepositories(workspace.root, workspace.indexDirectory).resolve(registered.toList())

private fun directories(layout: WorkspaceLayout) = layout.repositories.map { it.directory.value }

private fun codes(layout: WorkspaceLayout) = layout.notices.map { it.code.value }

class RegisteredCheckoutsTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    @Test
    fun `a root in a work tree is joined by a repository registered from elsewhere`() =
        TestWorkspace().use { workspace ->
            repositoryAt(workspace.root)
            val library = repositoryAt(workspace.root.resolveSibling("library"))
            val layout = layout(workspace, RegisteredRepository("kit", library))
            assertEquals(listOf("", "kit"), directories(layout))
            assertEquals(library, layout.repositories.last().checkout.root)
            assertEquals("library", layout.repositories.last().repository.name)
            assertEquals(emptyList(), layout.notices)
        }

    @Test
    fun `a plain root with one checkout and a registered repository holds both`() = TestWorkspace().use { workspace ->
        repositoryAt(workspace.root.resolve("app"))
        val library = repositoryAt(workspace.root.resolveSibling("library"))
        val layout = layout(workspace, RegisteredRepository("library", library))
        assertEquals(listOf("app", "library"), directories(layout))
    }

    @Test
    fun `a registered path that is no longer a work tree is left out with a notice`() =
        TestWorkspace().use { workspace ->
            repositoryAt(workspace.root)
            val gone = workspace.root.resolveSibling("gone")
            Files.createDirectories(gone)
            val layout = layout(workspace, RegisteredRepository("gone", gone))
            assertEquals(listOf(""), directories(layout))
            assertEquals(listOf("workspace.registered-repository-missing"), codes(layout))
        }

    @Test
    fun `a registered name that the root already holds is left out`() = TestWorkspace().use { workspace ->
        repositoryAt(workspace.root)
        Files.createDirectories(workspace.root.resolve("docs"))
        val library = repositoryAt(workspace.root.resolveSibling("library"))
        val layout = layout(workspace, RegisteredRepository("docs", library))
        assertEquals(listOf(""), directories(layout))
        assertEquals(listOf("workspace.repository-name-taken"), codes(layout))
    }

    @Test
    fun `a registered clone of a repository of the workspace is a duplicate`() = TestWorkspace().use { workspace ->
        val original = repositoryAt(workspace.root.resolve("library"))
        repositoryAt(workspace.root.resolve("app"))
        val copy = workspace.root.resolveSibling("library-copy")
        TestGit.output(workspace.root, "clone", "-q", original.toString(), copy.toString())
        val layout = layout(workspace, RegisteredRepository("library-copy", copy))
        assertEquals(listOf("app", "library"), directories(layout))
        assertEquals(listOf("workspace.duplicate-repository"), codes(layout))
    }

    @Test
    fun `a registered repository without a commit is left out`() = TestWorkspace().use { workspace ->
        repositoryAt(workspace.root)
        val empty = repositoryAt(workspace.root.resolveSibling("empty"), commit = false)
        val layout = layout(workspace, RegisteredRepository("empty", empty))
        assertEquals(listOf(""), directories(layout))
        assertEquals(listOf("workspace.repository-without-commit"), codes(layout))
    }

    @Test
    fun `a scan indexes a registered repository and the stamp follows its files`() = TestWorkspace().use { workspace ->
        repositoryAt(workspace.root)
        val library = repositoryAt(workspace.root.resolveSibling("library"))
        val registered = listOf(RegisteredRepository("library", library))
        val config = WorkspaceConfig(workspace.root, workspace.indexDirectory, registered = { registered })
        ScanEngine(coreRegistry(), config).use { engine ->
            engine.update()
            val scanned = engine.graph().structure().repositories.map { it.directory.value }.sorted()
            assertEquals(listOf("", "library"), scanned)
            val state = engine.workspaceState()
            val before = state.stamp()
            Files.writeString(library.resolve("NOTES.md"), "notes\n")
            assertNotEquals(before, state.stamp())
        }
    }

    @Test
    fun `a registered checkout that was missing when the observer started joins the stamp once restored`() =
        TestWorkspace().use { workspace ->
            repositoryAt(workspace.root)
            val library = workspace.root.resolveSibling("library")
            val registered = listOf(RegisteredRepository("library", library))
            val config = WorkspaceConfig(workspace.root, workspace.indexDirectory, registered = { registered })
            ScanEngine(coreRegistry(), config).use { engine ->
                val state = engine.workspaceState()
                val before = state.stamp()
                assertEquals(1, state.gitDirectories.size)

                repositoryAt(library)

                assertNotEquals(before, state.stamp())
                assertEquals(2, state.gitDirectories.size)
            }
        }

    @Test
    fun `a registered checkout without a commit when the observer started joins the stamp once committed`() =
        TestWorkspace().use { workspace ->
            repositoryAt(workspace.root)
            val library = repositoryAt(workspace.root.resolveSibling("library"), commit = false)
            val registered = listOf(RegisteredRepository("library", library))
            val config = WorkspaceConfig(workspace.root, workspace.indexDirectory, registered = { registered })
            ScanEngine(coreRegistry(), config).use { engine ->
                val state = engine.workspaceState()
                val before = state.stamp()

                TestGit.output(library, "add", "-A")
                TestGit.output(library, "commit", "-q", "-m", "initial")

                assertNotEquals(before, state.stamp())
                assertEquals(2, state.gitDirectories.size)
            }
        }
}
