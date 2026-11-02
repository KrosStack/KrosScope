package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.engine.structure.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorkspaceStampRepositoriesTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    @Test
    fun `an edit in either repository changes the stamp and files outside the checkouts do not`() =
        TwoRepositoryWorkspace().use { workspace ->
            workspace.engine().use { engine ->
                val source = engine.workspaceState()
                val initial = source.stamp()
                assertEquals(initial, source.stamp())
                workspace.write("notes.txt", "outside\n")
                assertEquals(initial, source.stamp())
                workspace.write("library/model/src/main/kotlin/org/example/library/Extra.kt", "class Extra\n")
                val edited = source.stamp()
                assertNotEquals(initial, edited)
                workspace.write("application/app/src/main/kotlin/org/example/app/Other.kt", "class Other\n")
                assertNotEquals(edited, source.stamp())
            }
        }

    @Test
    fun `a checkout added to the root changes the stamp`() = TwoRepositoryWorkspace().use { workspace ->
        workspace.engine().use { engine ->
            val source = engine.workspaceState()
            val initial = source.stamp()
            workspace.createRepository("tools", mapOf("Tool.kt" to "class Tool\n"))
            assertNotEquals(initial, source.stamp())
        }
    }

    @Test
    fun `a checkout skipped for lacking a commit joins the stamp after its first commit`() =
        TwoRepositoryWorkspace().use { workspace ->
            val files = mapOf("Tool.kt" to "class Tool")
            val pending = workspace.createRepository("tools", files, commit = false)
            workspace.engine().use { engine ->
                val source = engine.workspaceState()
                val initial = source.stamp()
                TestGit.output(pending, "add", "-A")
                TestGit.output(pending, "commit", "-q", "-m", "initial")
                assertNotEquals(initial, source.stamp())
            }
        }

    @Test
    fun `a commit that changes no file changes the stamp`() = TwoRepositoryWorkspace().use { workspace ->
        workspace.engine().use { engine ->
            val source = engine.workspaceState()
            val library = workspace.root.resolve("library")
            workspace.write("library/model/src/main/kotlin/org/example/library/Extra.kt", "class Extra\n")
            TestGit.output(library, "add", "-A")
            val staged = source.stamp()
            TestGit.output(library, "commit", "-q", "-m", "extra")
            assertNotEquals(staged, source.stamp())
        }
    }

    @Test
    fun `a commit after the references were packed changes the stamp`() = TwoRepositoryWorkspace().use { workspace ->
        workspace.engine().use { engine ->
            val source = engine.workspaceState()
            val library = workspace.root.resolve("library")
            TestGit.output(library, "pack-refs", "--all")
            val packed = source.stamp()
            TestGit.output(library, "commit", "-q", "--allow-empty", "-m", "again")
            assertNotEquals(packed, source.stamp())
        }
    }

    @Test
    fun `a commit in a linked worktree changes its stamp`() = TestWorkspace().use { workspace ->
        val main = Files.createTempDirectory("kscope-main")
        try {
            Files.writeString(main.resolve("Main.kt"), "class Main")
            TestGit.output(main, "init", "-q")
            TestGit.output(main, "add", "-A")
            TestGit.output(main, "commit", "-q", "-m", "initial")
            TestGit.output(main, "worktree", "add", "-q", "-b", "linked", workspace.root.toString())
            workspace.engine().use { engine ->
                val source = engine.workspaceState()
                val initial = source.stamp()
                TestGit.output(workspace.root, "commit", "-q", "--allow-empty", "-m", "linked")
                assertNotEquals(initial, source.stamp())
            }
        } finally {
            main.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a stamp taken without an observer changes with a commit too`() = TwoRepositoryWorkspace().use { workspace ->
        workspace.engine().use { engine ->
            val initial = engine.workspaceStamp()
            TestGit.output(workspace.root.resolve("library"), "commit", "-q", "--allow-empty", "-m", "again")
            assertNotEquals(initial, engine.workspaceStamp())
        }
    }

    @Test
    fun `the git directory of every repository is listed`() = TwoRepositoryWorkspace().use { workspace ->
        workspace.engine().use { engine ->
            val directories = engine.workspaceState().gitDirectories
            assertEquals(2, directories.size)
            assertTrue(directories.all { it.fileName.toString() == ".git" })
            assertEquals(listOf("application", "library"), directories.map { it.parent.fileName.toString() })
        }
    }
}
