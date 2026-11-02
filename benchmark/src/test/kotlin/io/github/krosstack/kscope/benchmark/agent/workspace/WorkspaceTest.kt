package io.github.krosstack.kscope.benchmark.agent.workspace

import io.github.krosstack.kscope.benchmark.TemporaryRepositoryTest
import io.github.krosstack.kscope.benchmark.TestGit
import io.github.krosstack.kscope.benchmark.agent.SingleRun
import io.github.krosstack.kscope.benchmark.agent.launch.ChildEnvironment
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceTest : TemporaryRepositoryTest("kscope-workspace-test") {
    private val git = TestGit.cli()
    private val repository = base.resolve("repo").also { Files.createDirectories(it) }
    private val source = repository.resolve("sub")

    private fun write(path: String, text: String) {
        val file = repository.resolve(path)
        Files.createDirectories(file.parent)
        Files.write(file, text.toByteArray())
    }

    private fun createSourceRepository() {
        git.output(repository, "init", "-q")
        write(".gitattributes", "* text=auto eol=lf\n")
        write(".gitignore", "build/\nlocal.properties\n")
        write("sub/.gitignore", "build/\nlocal.properties\n")
        write("sub/src/App.kt", "class App\r\nval a = 1\r\n")
        write("sub/gradlew", "#!/bin/sh\n")
        write("sub/data.bin", "\u0000\u0001\u0002")
        git.output(repository, "add", "-A")
        git.output(repository, "commit", "-q", "-m", "source")
        write("sub/build/out.txt", "generated")
        write("sub/local.properties", "sdk.dir=elsewhere")
        write("sub/src/Untracked.kt", "class Untracked")
    }

    private fun Workspace.put(path: String, text: String) {
        val file = root.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    private fun create(): Workspace {
        createSourceRepository()
        return WorkspaceFactory(TestGit.cli()).create(source, base.resolve("copy"))
    }

    @Test
    fun `the copy holds the indexed files with canonical line endings and nothing ignored or untracked`() {
        val workspace = create()

        assertEquals(setOf(".gitignore", "data.bin", "gradlew", "src/App.kt"), workspace.baseFiles())
        assertEquals("class App\nval a = 1\n", Files.readString(workspace.root.resolve("src/App.kt")))
        assertFalse(Files.exists(workspace.root.resolve("build")))
        assertFalse(Files.exists(workspace.root.resolve("local.properties")))
        assertFalse(Files.exists(workspace.root.resolve("src/Untracked.kt")))
    }

    @Test
    fun `the copy has one unsigned commit with its own identity without global or system configuration`() {
        createSourceRepository()
        val emptyConfig = Files.createFile(base.resolve("empty-gitconfig"))
        val environment = ChildEnvironment.with(
            ChildEnvironment.without(listOf("GIT_AUTHOR_", "GIT_COMMITTER_")),
            mapOf("GIT_CONFIG_GLOBAL" to emptyConfig.toString(), "GIT_CONFIG_NOSYSTEM" to "1"),
        )
        val workspace = WorkspaceFactory(GitCli(environment = environment)).create(source, base.resolve("copy"))

        assertEquals("1", git.output(workspace.root, "rev-list", "--count", "HEAD").trim())
        val identities = git.output(workspace.root, "log", "-1", "--format=%an %ae%n%cn %ce").trim()
        val identity = "benchmark benchmark@example.invalid"
        assertEquals("$identity\n$identity", identities)
        assertFalse(git.output(workspace.root, "cat-file", "commit", "HEAD").contains("\ngpgsig "))
        assertEquals("", git.output(workspace.root, "remote").trim())
        assertTrue(workspace.root.startsWith(base.toRealPath()))
    }

    @Test
    fun `changes are reported against the base and can be restored`() {
        val workspace = create()
        Files.writeString(workspace.root.resolve("src/App.kt"), "class App\nval a = 2\n")
        workspace.put("src/New.kt", "class New")
        Files.delete(workspace.root.resolve("gradlew"))

        assertEquals(listOf("gradlew", "src/App.kt", "src/New.kt"), workspace.changedFiles())

        workspace.restoreBase()

        assertEquals(emptyList(), workspace.changedFiles())
        assertEquals("class App\nval a = 1\n", Files.readString(workspace.root.resolve("src/App.kt")))
    }

    @Test
    fun `state directories of the agent programs are not counted as changes`() {
        val workspace = create()
        workspace.put(".claude/cache.json", "{}")
        workspace.put(".codex/state", "x")
        workspace.put("src/Real.kt", "class Real")

        assertEquals(listOf("src/Real.kt"), workspace.changedFiles())
    }

    @Test
    fun `a patch made with git applies to a restored copy`() {
        val workspace = create()
        Files.writeString(workspace.root.resolve("src/App.kt"), "class App\nval a = 3\n")
        val patch = base.resolve("change.patch")
        git.output(workspace.root, "add", "-A")
        Files.writeString(patch, git.output(workspace.root, "diff", "--cached", "--no-color", "HEAD"))
        workspace.restoreBase()

        assertTrue(workspace.applyPatch(patch))

        assertEquals(listOf("src/App.kt"), workspace.changedFiles())
        workspace.restoreBase()
        assertFalse(workspace.applyPatch(base.resolve("missing.patch")))
    }

    @Test
    fun `forbidden instruction and planning entries are found anywhere but inside the git directory`() {
        val workspace = create()
        workspace.put("CLAUDE.md", "notes")
        workspace.put("deep/.claude/settings.json", "{}")

        val found = workspace.forbiddenEntries(SingleRun.FORBIDDEN_NAMES)

        assertEquals(listOf(".claude", "CLAUDE.md"), found.map { it.substringAfterLast('/') }.sorted())
    }

    @Test
    fun `hidden files are copied over existing ones`() {
        val workspace = create()
        val hidden = base.resolve("hidden")
        Files.createDirectories(hidden.resolve("src"))
        Files.writeString(hidden.resolve("src/App.kt"), "class Hidden")
        Files.writeString(hidden.resolve("src/Extra.kt"), "class Extra")

        workspace.copyIn(hidden)

        assertEquals("class Hidden", Files.readString(workspace.root.resolve("src/App.kt")))
        assertTrue(Files.exists(workspace.root.resolve("src/Extra.kt")))
    }
}
