package io.github.krosstack.kscope.engine.structure

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StructureNestedIgnoreTest {
    @Test
    fun `outer ignore rules also exclude paths inside a visible nested repository`() {
        assumeTrue(TestGit.available())
        StructureWorkspace().use { workspace ->
            TestGit.output(workspace.root, "init", "-q")
            workspace.file("settings.gradle.kts")
            workspace.file(".gitignore", "nested/settings.gradle.kts\nscratch/\n")
            workspace.file("nested/settings.gradle.kts")
            workspace.file("nested/scratch/clone/package.json")
            TestGit.output(workspace.root.resolve("nested"), "init", "-q")
            TestGit.output(workspace.root.resolve("nested/scratch/clone"), "init", "-q")

            val found = StructureScanner(4).scan(workspace.root)

            assertEquals(listOf("."), found.builds.map { it.directory })
            assertEquals(listOf("nested"), found.repositories.map { it.directory })
            assertTrue(found.projects.isEmpty())
            assertEquals(1, found.ignoredDirectoriesSkipped)
            assertNull(found.ignoreRulesNotice)
        }
    }

    @Test
    fun `Git handles local excludes negation and non ASCII directory names`() {
        assumeTrue(TestGit.available())
        StructureWorkspace().use { workspace ->
            TestGit.output(workspace.root, "init", "-q")
            workspace.file(".gitignore", "tools/*\n!tools/kept/\n")
            workspace.file(".git/info/exclude", "café/\n")
            workspace.file("tools/ignored/settings.gradle.kts")
            workspace.file("tools/kept/settings.gradle.kts")
            workspace.file("café/settings.gradle.kts")

            val found = StructureScanner(4).scan(workspace.root)

            assertEquals(listOf("tools/kept"), found.builds.map { it.directory })
            assertEquals(2, found.ignoredDirectoriesSkipped)
            assertNull(found.ignoreRulesNotice)
        }
    }
}
