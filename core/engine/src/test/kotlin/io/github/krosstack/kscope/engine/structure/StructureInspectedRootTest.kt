package io.github.krosstack.kscope.engine.structure

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StructureInspectedRootTest {
    @Test
    fun `a final recursive negation does not hide the inspected build root`() {
        assumeTrue(TestGit.available())
        StructureWorkspace().use { workspace ->
            TestGit.output(workspace.root, "init", "-q")
            workspace.file(".gitignore", "scratch/\r\n*.log\r\n\r\n# Source packages\r\n!**/src/**/build/\r\n")
            workspace.file("settings.gradle.kts")
            workspace.file("kept/settings.gradle.kts")
            workspace.file("scratch/project/settings.gradle.kts")

            val found = StructureScanner(4).scan(workspace.root)

            assertEquals(2, found.directoriesVisited)
            assertEquals(listOf(".", "kept"), found.builds.map { it.directory })
            assertEquals(1, found.ignoredDirectoriesSkipped)
            assertNull(found.ignoreRulesNotice)
        }
    }

    @Test
    fun `direct inspection inside an ignored ancestor keeps descendant builds visible`() {
        assumeTrue(TestGit.available())
        for (pattern in ancestorPatterns) verifyIgnoredAncestor(pattern)
    }

    private fun verifyIgnoredAncestor(pattern: String) {
        StructureWorkspace().use { workspace ->
            TestGit.output(workspace.root, "init", "-q")
            workspace.file(".gitignore", "$pattern\n")
            workspace.file("ignored-dir/project/settings.gradle.kts")
            workspace.file("ignored-dir/project/child/settings.gradle.kts")

            val found = StructureScanner(4).scan(workspace.root.resolve("ignored-dir/project"))

            assertEquals(2, found.directoriesVisited)
            assertEquals(listOf(".", "child"), found.builds.map { it.directory })
            assertEquals(0, found.ignoredDirectoriesSkipped)
            assertNull(found.ignoreRulesNotice)
        }
    }

    private companion object {
        val ancestorPatterns = listOf(
            "ignored-dir/",
            "/ignored-dir/",
            "ignored-*/",
            "**/ignored-dir/",
            "ignored-dir/*",
            "ignored-dir/**",
            "ignored-dir/**/project/",
            "ignored-dir/proje?t/",
            "ignored-dir/[p]roject/",
            "ignored-dir/project",
        )
    }
}
