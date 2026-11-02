package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.TwoRepositoryWorkspace
import io.github.krosstack.kscope.engine.structure.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun layoutOf(root: Path, index: Path) = WorkspaceRepositories(root, index).resolve()

private fun directories(layout: WorkspaceLayout) = layout.repositories.map { it.directory.value }

private fun rootCommit(directory: Path) = TestGit.output(directory, "rev-list", "--max-parents=0", "HEAD")

class WorkspaceRepositoriesTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    @Test
    fun `a root inside a work tree is one repository even when it holds other checkouts`() =
        TwoRepositoryWorkspace().use { workspace ->
            TestGit.output(workspace.root, "init", "-q")
            val layout = layoutOf(workspace.root, workspace.indexDirectory)
            assertEquals(listOf(""), directories(layout))
            assertEquals(workspace.root.toAbsolutePath().normalize(), layout.repositories.single().checkout.root)
            assertEquals(emptyList(), layout.notices)
        }

    @Test
    fun `two checkouts below a plain root are two repositories in directory order`() =
        TwoRepositoryWorkspace().use { workspace ->
            val layout = layoutOf(workspace.root, workspace.indexDirectory)
            assertEquals(listOf("application", "library"), directories(layout))
            val library = layout.repositories.last()
            assertEquals(rootCommit(workspace.root.resolve("library")), library.repository.id.value)
            assertEquals("library", library.repository.name)
            assertEquals(workspace.root.resolve("library").toAbsolutePath().normalize(), library.checkout.root)
            assertEquals(emptyList(), layout.notices)
        }

    @Test
    fun `one checkout below a plain root leaves the root walked as one repository`() =
        TestWorkspace("plain").use { workspace ->
            workspace.write("tool/README.md", "tool\n")
            TestGit.output(workspace.root.resolve("tool"), "init", "-q")
            val layout = layoutOf(workspace.root, workspace.indexDirectory)
            assertEquals(listOf(""), directories(layout))
            assertEquals(workspace.root.toAbsolutePath().normalize(), layout.repositories.single().checkout.root)
            assertEquals(GitAccess.NotARepository, layout.repositories.single().git)
        }

    @Test
    fun `hidden directories and symbolic links are not candidates`() = TwoRepositoryWorkspace().use { workspace ->
        workspace.createRepository(".hidden", mapOf("a.txt" to "hidden\n"))
        val link = workspace.root.resolve("alias")
        try {
            Files.createSymbolicLink(link, workspace.root.resolve("library"))
        } catch (_: IOException) {
            Files.createDirectory(link)
        } catch (_: UnsupportedOperationException) {
            Files.createDirectory(link)
        }
        assertEquals(listOf("application", "library"), directories(layoutOf(workspace.root, workspace.indexDirectory)))
    }

    @Test
    fun `a checkout whose git entry is a pointer file is a repository`() = TwoRepositoryWorkspace().use { workspace ->
        val main = workspace.root.resolveSibling("main-checkout")
        Files.createDirectories(main)
        Files.writeString(main.resolve("Main.kt"), "class Main\n")
        TestGit.output(main, "init", "-q")
        TestGit.output(main, "add", "-A")
        TestGit.output(main, "commit", "-q", "-m", "initial")
        TestGit.output(main, "worktree", "add", "-q", workspace.root.resolve("linked").toString(), "-b", "linked")
        assertTrue(Files.isRegularFile(workspace.root.resolve("linked/.git")))
        val layout = layoutOf(workspace.root, workspace.indexDirectory)
        assertEquals(listOf("application", "library", "linked"), directories(layout))
        assertEquals(rootCommit(main), layout.repositories.last().repository.id.value)
    }

    @Test
    fun `a second checkout with the same id is skipped and reported`() = TwoRepositoryWorkspace().use { workspace ->
        TestGit.output(workspace.root, "clone", "-q", "library", "clone")
        val layout = layoutOf(workspace.root, workspace.indexDirectory)
        assertEquals(listOf("application", "clone"), directories(layout))
        val notice = layout.notices.single()
        assertEquals("workspace.duplicate-repository", notice.code.value)
        assertTrue("library" in notice.message && "clone" in notice.message, notice.message)
    }

    @Test
    fun `a checkout without a commit is skipped and reported`() = TwoRepositoryWorkspace().use { workspace ->
        workspace.createRepository("empty", mapOf("a.txt" to "x\n"), commit = false)
        val layout = layoutOf(workspace.root, workspace.indexDirectory)
        assertEquals(listOf("application", "library"), directories(layout))
        val notice = layout.notices.single()
        assertEquals("workspace.repository-without-commit", notice.code.value)
        assertTrue("empty" in notice.message, notice.message)
    }

    @Test
    fun `two checkouts without a commit leave no repository and do not turn the root into one`() =
        TestWorkspace().use { workspace ->
            listOf("first", "second").forEach { name ->
                workspace.write("$name/a.txt", "x")
                TestGit.output(workspace.root.resolve(name), "init", "-q")
            }
            val layout = layoutOf(workspace.root, workspace.indexDirectory)
            assertEquals(emptyList(), directories(layout))
            assertEquals(listOf("first", "second"), layout.withoutCommit)
            assertEquals(2, layout.notices.count { it.code.value == "workspace.repository-without-commit" })
        }

    @Test
    fun `files of the root outside the checkouts are counted in one notice`() =
        TwoRepositoryWorkspace().use { workspace ->
            workspace.write("notes.txt", "notes\n")
            workspace.write("docs/guide.md", "guide\n")
            workspace.write("kscope.toml", "\n")
            val layout = layoutOf(workspace.root, workspace.indexDirectory)
            val notice = layout.notices.single()
            assertEquals("workspace.files-outside-repositories", notice.code.value)
            assertTrue("2 files" in notice.message, notice.message)
            assertEquals(listOf("application", "library"), directories(layout))
        }
}
