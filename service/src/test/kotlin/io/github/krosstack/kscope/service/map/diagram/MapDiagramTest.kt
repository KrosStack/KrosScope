package io.github.krosstack.kscope.service.map.diagram

import io.github.krosstack.kscope.diagram.DiagramKind
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.map.WorkspaceMap
import io.github.krosstack.kscope.service.map.workspaceMap
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapDiagramTest {
    private fun write(root: Path, relative: String, text: String) {
        val file = root.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    private fun <T> scanned(block: (KrosScope, WorkspaceMap) -> T): T {
        val workspace = Files.createTempDirectory("kscope-map-diagram")
        try {
            write(workspace, "settings.gradle.kts", "include(\":lib\")\n")
            write(workspace, "lib/build.gradle.kts", "")
            write(workspace, "lib/src/main/kotlin/Shapes.kt", SHAPES)
            write(workspace, "lib/src/main/kotlin/Report.kt", REPORT)
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
    fun `a function of the map is drawn and its node carries its symbol id and its file`() {
        scanned { scope, map ->
            val id = symbolId(map, "Report.kt", "total")

            val answer = scope.mapDiagram(map, id, 1, 60)

            val diagram = assertNotNull(answer.diagram)
            assertEquals(DiagramKind.SYMBOL, diagram.kind)
            val selected = diagram.nodes.single { it.selected }
            assertEquals(id, selected.symbolId)
            assertEquals(fileNode(map, "Report.kt"), answer.files[id])
            assertNull(answer.error)
        }
    }

    @Test
    fun `a type of the map is drawn, without a limit on its depth`() {
        scanned { scope, map ->
            val id = symbolId(map, "Shapes.kt", "Circle")

            val answer = scope.mapDiagram(map, id, null, 60)

            val diagram = assertNotNull(answer.diagram)
            assertEquals("shapes.Circle", diagram.nodes.single { it.selected }.label)
        }
    }

    @Test
    fun `a symbol that a diagram does not draw is an error value that names its kind`() {
        scanned { scope, map ->
            val answer = scope.mapDiagram(map, symbolId(map, "Shapes.kt", "radius"), 1, 60)

            assertNull(answer.diagram)
            assertTrue(assertNotNull(answer.error).contains("property"), answer.toString())
        }
    }

    @Test
    fun `a name that is no symbol id of the map is an error value, also when a qualified name would pick one`() {
        scanned { scope, map ->
            val byName = scope.mapDiagram(map, "shapes.Circle", 1, 60)
            val absent = scope.mapDiagram(map, "absent", 1, 60)

            assertEquals("The map has no such symbol.", byName.error)
            assertEquals("The map has no such symbol.", absent.error)
            assertNull(byName.diagram)
        }
    }

    @Test
    fun `links bound the lines and the same question gets the same answer`() {
        scanned { scope, map ->
            val id = symbolId(map, "Report.kt", "total")

            val none = scope.mapDiagram(map, id, 1, 0)

            assertEquals(none, scope.mapDiagram(map, id, 1, 0))
            assertTrue(assertNotNull(none.diagram).edges.isEmpty())
        }
    }

    private companion object {
        const val SHAPES =
            "package shapes\n\nclass Circle {\n    val radius: Int = 1\n\n    fun area(): Int = radius\n}\n"
        const val REPORT =
            "package shapes\n\nclass Report(private val circle: Circle) {\n    fun total(): Int = circle.area()\n}\n"
    }
}
