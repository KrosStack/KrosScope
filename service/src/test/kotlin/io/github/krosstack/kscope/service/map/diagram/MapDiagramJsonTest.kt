package io.github.krosstack.kscope.service.map.diagram

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramGroup
import io.github.krosstack.kscope.diagram.DiagramKind
import io.github.krosstack.kscope.diagram.DiagramNode
import io.github.krosstack.kscope.diagram.Omission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapDiagramJsonTest {
    @Test
    fun `an answer that is an error holds nothing else`() {
        val text = MapDiagramJson.encode(MapDiagram(error = "No such symbol."))

        assertEquals("{\"error\":\"No such symbol.\"}", text)
    }

    @Test
    fun `a diagram keeps the fields that the diagram command prints, including a group that is null`() {
        val diagram = diagramOf(
            listOf(DiagramNode("n1", "fun area()", "kotlin.function", null, selected = true, symbolId = "fn:area")),
            listOf(DiagramGroup("g1", "Report.kt", emptyList())),
        )

        val text = MapDiagramJson.encode(MapDiagram(diagram, mapOf("fn:area" to "f1")))

        assertTrue(text.contains("\"group\":null"), text)
        assertTrue(text.contains("\"selected\":true"), text)
        assertTrue(text.contains("\"symbolId\":\"fn:area\""), text)
        assertTrue(text.contains("\"files\":{\"fn:area\":\"f1\"}"), text)
        assertFalse(text.contains("error"), text)
    }

    private fun diagramOf(nodes: List<DiagramNode>, groups: List<DiagramGroup>) = Diagram(
        DiagramKind.SYMBOL,
        "Call flow",
        nodes,
        groups,
        emptyList(),
        emptyList(),
        Omission(0, emptyMap()),
        emptyList(),
    )
}
