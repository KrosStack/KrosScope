package io.github.krosstack.kscope.engine.structure

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StructureIgnoreTest {
    @Test
    fun `an ignored directory hides its repositories builds and project markers`() {
        assumeTrue(TestGit.available())
        StructureWorkspace().use { workspace ->
            TestGit.output(workspace.root, "init", "-q")
            workspace.file("settings.gradle.kts")
            workspace.file(".gitignore", "scratch/\n")
            workspace.file("scratch/clone/settings.gradle.kts")
            workspace.file("scratch/clone/package.json")
            TestGit.output(workspace.root.resolve("scratch/clone"), "init", "-q")

            val found = StructureScanner(4).scan(workspace.root)

            assertEquals(listOf("."), found.builds.map { it.directory })
            assertTrue(found.repositories.isEmpty())
            assertTrue(found.projects.isEmpty())
            assertEquals(1, found.directoriesVisited)
            assertEquals(1, found.ignoredDirectoriesSkipped)
            assertNull(found.ignoreRulesNotice)
        }
    }

    @Test
    fun `a nested repository that is not ignored remains visible`() {
        assumeTrue(TestGit.available())
        StructureWorkspace().use { workspace ->
            TestGit.output(workspace.root, "init", "-q")
            workspace.file("settings.gradle.kts")
            workspace.file("scratch/clone/settings.gradle.kts")
            TestGit.output(workspace.root.resolve("scratch/clone"), "init", "-q")

            val found = StructureScanner(4).scan(workspace.root)

            assertEquals(listOf(".", "scratch/clone"), found.builds.map { it.directory })
            assertEquals(listOf("scratch/clone"), found.repositories.map { it.directory })
            assertEquals(0, found.ignoredDirectoriesSkipped)
        }
    }

    @Test
    fun `a directory outside a work tree still reports its two repositories`() {
        assumeTrue(TestGit.available())
        StructureWorkspace().use { workspace ->
            workspace.file("first/settings.gradle.kts")
            workspace.file("second/settings.gradle.kts")
            workspace.file(".gitignore", "second/\n")
            TestGit.output(workspace.root.resolve("first"), "init", "-q")
            TestGit.output(workspace.root.resolve("second"), "init", "-q")

            val found = StructureScanner(4).scan(workspace.root)

            assertNull(found.checkout)
            assertEquals(listOf("first", "second"), found.repositories.map { it.directory })
            assertEquals(listOf("first", "second"), found.builds.map { it.directory })
        }
    }

    @Test
    fun `an inspected subdirectory uses the enclosing work trees ignore rules`() {
        assumeTrue(TestGit.available())
        StructureWorkspace().use { workspace ->
            TestGit.output(workspace.root, "init", "-q")
            workspace.file(".gitignore", "area/scratch/\n")
            workspace.file("area/settings.gradle.kts")
            workspace.file("area/scratch/clone/settings.gradle.kts")
            TestGit.output(workspace.root.resolve("area/scratch/clone"), "init", "-q")

            val found = StructureScanner(4).scan(workspace.root.resolve("area"))

            assertEquals(listOf("."), found.builds.map { it.directory })
            assertTrue(found.repositories.isEmpty())
        }
    }

    @Test
    fun `ignored marker files are skipped without hiding an unignored project`() {
        assumeTrue(TestGit.available())
        StructureWorkspace().use { workspace ->
            TestGit.output(workspace.root, "init", "-q")
            workspace.file(".gitignore", "settings.gradle.kts\npackage.json\n")
            workspace.file("settings.gradle.kts")
            workspace.file("package.json")
            workspace.file("Cargo.toml")

            val found = StructureScanner(4).scan(workspace.root)

            assertTrue(found.builds.isEmpty())
            assertEquals(listOf("cargo"), found.projects.map { it.ecosystem })
        }
    }

    @Test
    fun `an ignored linked worktree remains in the repository worktree list`() {
        assumeTrue(TestGit.available())
        StructureWorkspace().use { workspace ->
            TestGit.output(workspace.root, "init", "-q")
            workspace.file("settings.gradle.kts")
            TestGit.output(workspace.root, "add", "settings.gradle.kts")
            TestGit.output(workspace.root, "commit", "-q", "-m", "initial")
            workspace.file(".gitignore", "scratch/\n")
            TestGit.output(workspace.root, "worktree", "add", "-q", "-b", "feature", "scratch/feature")

            val found = StructureScanner(4).scan(workspace.root)

            assertEquals(listOf("scratch/feature"), found.worktrees.map { it.directory })
            assertTrue(found.repositories.isEmpty())
            assertEquals(listOf("."), found.builds.map { it.directory })
        }
    }

    @Test
    fun `without Git the scanner keeps the directory walk and reports the fallback`() {
        StructureWorkspace().use { workspace ->
            workspace.repository(".")
            workspace.file(".gitignore", "scratch/\n")
            workspace.file("settings.gradle.kts")
            workspace.file("scratch/settings.gradle.kts")
            workspace.repository("scratch")

            val found = StructureScanner(4, StructureScanner.DEFAULT_MAX_DIRECTORIES, "missing-structure-git")
                .scan(workspace.root)

            assertEquals(listOf(".", "scratch"), found.builds.map { it.directory })
            assertEquals(listOf("scratch"), found.repositories.map { it.directory })
            assertEquals(0, found.ignoredDirectoriesSkipped)
            assertEquals("Git ignore rules were not applied: Git is unavailable.", found.ignoreRulesNotice)
        }
    }
}
