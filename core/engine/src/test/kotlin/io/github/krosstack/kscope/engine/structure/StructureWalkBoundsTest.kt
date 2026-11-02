package io.github.krosstack.kscope.engine.structure

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StructureWalkBoundsTest {
    private fun scan(workspace: StructureWorkspace, depth: Int = StructureScanner.DEFAULT_DEPTH) =
        StructureScanner(depth).scan(workspace.root)

    @Test
    fun `the walk stops at the depth and a larger depth reaches further`() = StructureWorkspace().use { workspace ->
        workspace.file("a/b/c/settings.gradle.kts")

        assertTrue(scan(workspace, depth = 2).builds.isEmpty())
        assertEquals(listOf("a/b/c"), scan(workspace, depth = 3).builds.map { it.directory })
    }

    @Test
    fun `directories that a scan ignores are not entered`() = StructureWorkspace().use { workspace ->
        workspace.file("node_modules/pkg/package.json")
        workspace.file("build/generated/settings.gradle.kts")
        workspace.file(".gradle/cache/settings.gradle.kts")
        workspace.file("kept/package.json")

        val found = scan(workspace)

        assertEquals(listOf("kept"), found.projects.map { it.directory })
        assertTrue(found.builds.isEmpty())
    }

    @Test
    fun `a symbolic link is not followed out of the directory`() {
        StructureWorkspace().use { workspace ->
            StructureWorkspace().use { outside ->
                outside.file("settings.gradle.kts")
                outside.file("package.json")
                if (!linked(workspace.root.resolve("link"), outside.root)) return

                val found = scan(workspace)

                assertTrue(found.builds.isEmpty(), found.builds.toString())
                assertTrue(found.projects.isEmpty(), found.projects.toString())
            }
        }
    }

    private fun linked(link: Path, target: Path): Boolean = try {
        Files.createSymbolicLink(link, target)
        true
    } catch (_: UnsupportedOperationException) {
        false
    } catch (_: IOException) {
        false
    }

    @Test
    fun `the walk stops at the limit of directories and says so`() = StructureWorkspace().use { workspace ->
        for (index in 1..10) workspace.file("d$index/package.json")

        val found = StructureScanner(maxDepth = 4, maxDirectories = 3).scan(workspace.root)

        assertTrue(found.truncated)
        assertTrue(found.directoriesVisited <= 3)
        assertFalse(StructureScanner(4).scan(workspace.root).truncated)
    }

    @Test
    fun `an inspected directory with no markers reports nothing`() = StructureWorkspace().use { workspace ->
        workspace.file("notes.txt", "text")

        val found = scan(workspace)

        assertTrue(found.builds.isEmpty())
        assertTrue(found.projects.isEmpty())
        assertTrue(found.repositories.isEmpty())
        assertNull(found.worktrees.firstOrNull())
    }
}
