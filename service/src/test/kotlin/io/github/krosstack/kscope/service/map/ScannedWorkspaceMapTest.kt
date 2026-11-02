package io.github.krosstack.kscope.service.map

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.NotIndexedException
import io.github.krosstack.kscope.service.indexRevision
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScannedWorkspaceMapTest {
    private fun inWorkspace(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("kscope-map")
        try {
            Files.writeString(root.resolve("settings.gradle.kts"), "include(\":lib\")\n")
            val sources = Files.createDirectories(root.resolve("lib/src/main/kotlin"))
            Files.writeString(root.resolve("lib/build.gradle.kts"), "")
            Files.writeString(sources.resolve("Lib.kt"), "class Lib {\n    fun greet() = \"hello\"\n}\n")
            Files.writeString(sources.resolve("Use.kt"), "class Use {\n    fun go() = Lib().greet()\n}\n")
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a workspace that was never scanned has no map`() = inWorkspace { root ->
        KrosScope.open(root).use { service ->
            assertFailsWith<NotIndexedException> { service.workspaceMap() }
        }
    }

    @Test
    fun `the map of a scanned workspace holds its modules files and symbols`() = inWorkspace { root ->
        KrosScope.open(root).use { service ->
            service.scan()
            val map = service.workspaceMap()
            val labels = map.overview.nodes.map { it.label }

            assertTrue(":lib" in labels)
            assertTrue("Lib.kt" in labels)
            assertTrue(map.overview.nodes.sumOf { it.symbols } >= 4)
            assertEquals(map.overview, service.workspaceMap().overview)
        }
    }

    @Test
    fun `the impact of a file reaches the file that uses it`() = inWorkspace { root ->
        KrosScope.open(root).use { service ->
            service.scan()
            val map = service.workspaceMap()
            val lib = map.overview.nodes.first { it.label == "Lib.kt" }

            val impact = service.mapImpact(map, nodeId = lib.id)

            assertEquals(null, impact.error)
            assertTrue(impact.affected.any { map.overview.nodes[it.node].label == "Use.kt" })
        }
    }

    @Test
    fun `an impact question that cannot be answered returns an error and does not throw`() = inWorkspace { root ->
        KrosScope.open(root).use { service ->
            service.scan()
            val map = service.workspaceMap()

            assertNotNull(service.mapImpact(map, nodeId = "w:").error)
            assertNotNull(service.mapImpact(map, nodeId = "f:repo//nothing.kt").error)
            assertNotNull(service.mapImpact(map, symbolId = "nothing").error)
            assertNotNull(service.mapImpact(map).error)
        }
    }

    @Test
    fun `a task is ranked in positions of the tree`() = inWorkspace { root ->
        KrosScope.open(root).use { service ->
            service.scan()
            val map = service.workspaceMap()

            val task = service.mapTask(map, "greet")

            assertTrue(task.files.any { map.overview.nodes[it.node].label == "Lib.kt" })
            assertTrue(task.files.all { it.reasons.isNotEmpty() })
        }
    }

    @Test
    fun `the revision of the index grows when a scan changes it`() = inWorkspace { root ->
        KrosScope.open(root).use { service ->
            service.scan()
            val before = service.indexRevision()

            service.scan()

            assertTrue(service.indexRevision() > before)
        }
    }
}
