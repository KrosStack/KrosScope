package io.github.krosstack.kscope.benchmark.agent.workspace

import io.github.krosstack.kscope.benchmark.TemporaryRepositoryTest
import io.github.krosstack.kscope.benchmark.TestGit
import io.github.krosstack.kscope.benchmark.agent.launch.ChildEnvironment
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkspaceIsolationTest : TemporaryRepositoryTest("kscope-workspace-isolation-test") {
    private val git = TestGit.cli()
    private val repository = base.resolve("repo").also { Files.createDirectories(it) }
    private val source = repository.resolve("sub")

    private fun createSourceRepository() {
        git.output(repository, "init", "-q")
        Files.createDirectories(source.resolve("src"))
        Files.writeString(source.resolve("src/App.kt"), "class App\n")
        git.output(repository, "add", "-A")
        git.output(repository, "commit", "-q", "-m", "source")
        Files.writeString(source.resolve("src/Staged.kt"), "class Staged\n")
        git.output(repository, "add", "sub/src/Staged.kt")
    }

    private fun topLevel(directory: Path): Path =
        Path.of(git.output(directory, "rev-parse", "--show-toplevel").trim()).toRealPath()

    @Test
    fun `repository selection in the environment never sends the setup into the source repository`() {
        createSourceRepository()
        val gitDirectory = repository.resolve(".git")
        val selection = mapOf(
            "GIT_DIR" to gitDirectory.toString(),
            "GIT_WORK_TREE" to repository.toString(),
            "GIT_INDEX_FILE" to gitDirectory.resolve("index").toString(),
        )
        val head = git.output(repository, "rev-parse", "HEAD")
        val index = Files.readAllBytes(gitDirectory.resolve("index"))
        val status = git.output(repository, "status", "--porcelain", "--untracked-files=all")

        val workspace = WorkspaceFactory(GitCli(environment = ChildEnvironment.with(System.getenv(), selection)))
            .create(source, base.resolve("copy"))

        assertEquals(head, git.output(repository, "rev-parse", "HEAD"))
        assertContentEquals(index, Files.readAllBytes(gitDirectory.resolve("index")))
        assertEquals(status, git.output(repository, "status", "--porcelain", "--untracked-files=all"))
        assertEquals("1", git.output(workspace.root, "rev-list", "--count", "HEAD").trim())
        assertEquals(workspace.root, topLevel(workspace.root))
    }

    @Test
    fun `a directory that is not the root of its own repository is refused before it is changed`() {
        createSourceRepository()
        val outside = Files.createDirectories(base.resolve("outside"))
        val nested = Files.createDirectories(source.resolve("nested"))
        val before = git.output(repository, "status", "--porcelain", "--untracked-files=all")

        assertFailsWith<IllegalStateException> { Workspace(nested.toRealPath(), git).restoreBase() }
        assertFailsWith<IllegalStateException> { Workspace(outside.toRealPath(), git).changedFiles() }

        assertEquals(before, git.output(repository, "status", "--porcelain", "--untracked-files=all"))
    }
}
