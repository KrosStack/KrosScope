package io.github.krosstack.kscope.service.map.diagram

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.map.WorkspaceMap
import io.github.krosstack.kscope.service.map.workspaceMap
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class MapDiagramTwinsTest {
    private fun write(root: Path, relative: String, text: String) {
        val file = root.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    private fun <T> scanned(block: (KrosScope, WorkspaceMap) -> T): T {
        val workspace = Files.createTempDirectory("kscope-map-twins")
        try {
            write(workspace, "settings.gradle.kts", "include(\":lib\", \":a\", \":b\")\n")
            write(workspace, "lib/build.gradle.kts", "")
            write(workspace, "lib/src/main/kotlin/Shared.kt", "package lib\n\nfun shared(): Int = 1\n")
            listOf("a" to "CheckoutA", "b" to "CheckoutB").forEach { (module, file) ->
                write(workspace, "$module/build.gradle.kts", NEEDS_LIB)
                write(workspace, "$module/src/main/kotlin/$file.kt", TWIN)
            }
            return KrosScope.open(workspace).use { scope ->
                scope.scan()
                block(scope, scope.workspaceMap())
            }
        } finally {
            workspace.toFile().deleteRecursively()
        }
    }

    private fun fileNode(map: WorkspaceMap, file: String): String = map.overview.nodes.first { it.label == file }.id

    private fun symbolId(map: WorkspaceMap, file: String, name: String): String =
        checkNotNull(map.detail(fileNode(map, file))).symbols.first { it.name == name }.id

    @Test
    fun `two different symbols with the same label and group are told apart by the id of their node`() {
        scanned { scope, map ->
            val first = symbolId(map, "CheckoutA.kt", "run")
            val second = symbolId(map, "CheckoutB.kt", "run")

            val answer = scope.mapDiagram(map, symbolId(map, "Shared.kt", "shared"), 1, 60)

            val diagram = assertNotNull(answer.diagram)
            val twins = diagram.nodes.filterNot { it.selected }.groupBy { it.label }.values.single()
            assertEquals(2, twins.size)
            val groups = twins.map { node -> diagram.groups.single { node.id in it.members }.label }
            assertEquals(1, groups.distinct().size)
            assertNotEquals(first, second)
            assertEquals(setOf(first, second), twins.mapNotNull { it.symbolId }.toSet())
            assertEquals(fileNode(map, "CheckoutA.kt"), answer.files[first])
            assertEquals(fileNode(map, "CheckoutB.kt"), answer.files[second])
        }
    }

    @Test
    fun `every node that carries an id carries the id of a symbol of the map, and a node without one is not tied`() {
        scanned { scope, map ->
            val answer = scope.mapDiagram(map, symbolId(map, "Shared.kt", "shared"), 1, 60)

            val diagram = assertNotNull(answer.diagram)
            val ids = diagram.nodes.mapNotNull { it.symbolId }
            assertEquals(ids.toSet(), answer.files.keys)
            assertEquals(ids.size, ids.toSet().size)
        }
    }

    private companion object {
        const val NEEDS_LIB = "dependencies {\n    implementation(project(\":lib\"))\n}\n"
        const val TWIN = "package app\n\nimport lib.shared\n\nclass Checkout {\n    fun run(): Int = shared()\n}\n"
    }
}
