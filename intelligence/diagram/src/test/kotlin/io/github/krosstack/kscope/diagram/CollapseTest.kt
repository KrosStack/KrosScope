package io.github.krosstack.kscope.diagram

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class CollapseTest {
    @Test
    fun `parallel edges preserve both evidence and state counts and draw the weakest`() {
        val diagram = (
            DiagramBuilder(DiagramGraph()).build(
                DiagramRequest(DiagramKind.MODULES),
            ) as DiagramResult.Built
            ).diagram
        val edge = diagram.edges.single { it.from == "n1" && it.to == "n2" }
        assertEquals(
            DiagramEdge(
                "n1",
                "n2",
                "depends_on [cycle]; build-model=1,convention=1; exact=1,heuristic=1",
                DrawnState.HEURISTIC,
                mapOf(DrawnState.EXACT to 1, DrawnState.HEURISTIC to 1),
                mapOf("BUILD_MODEL" to 1, "CONVENTION" to 1),
            ),
            edge,
        )
    }

    @Test
    fun `module dependency cycles are marked`() {
        val diagram = (
            DiagramBuilder(DiagramGraph()).build(
                DiagramRequest(DiagramKind.MODULES),
            ) as DiagramResult.Built
            ).diagram
        assertEquals(2, diagram.edges.count { "[cycle]" in it.label })
    }
}
