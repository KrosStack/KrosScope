package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramKind
import io.github.krosstack.kscope.diagram.DiagramRequest
import io.github.krosstack.kscope.diagram.DiagramResult
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class ReadBudgetTest {
    private fun typeChain(length: Int): SymbolWorld {
        val world = SymbolWorld()
        val entries = (0 until length).map { index ->
            val type = world.type("T${index.toString().padStart(3, '0')}")
            world.function("run", type)
        }
        entries.zipWithNext().forEachIndexed { index, (from, to) -> world.call(from, to, index + 1) }
        return world
    }

    private fun walked(world: SymbolWorld, node: String, budget: Int): Diagram {
        val request = DiagramRequest(DiagramKind.SYMBOL, node, 0, Int.MAX_VALUE, Int.MAX_VALUE)
        val result = SymbolDiagramBuilder(SymbolGraph(world), budget).build(request) { DiagramResult.Built(it) }
        return assertIs<DiagramResult.Built>(result).diagram
    }

    @Test
    fun `the budget of reader calls ends a long walk downstream`() {
        val graph = SymbolGraph(chainWorld(300))
        val request = DiagramRequest(DiagramKind.SYMBOL, "app.f0000", 0, Int.MAX_VALUE, Int.MAX_VALUE)
        val result = SymbolDiagramBuilder(graph, 40).build(request) { DiagramResult.Built(it) }
        val diagram = assertIs<DiagramResult.Built>(result).diagram
        assertTrue(graph.log.size <= 40 + 3)
        assertTrue(diagram.omitted.notExpanded > 0)
        assertTrue(diagram.nodeLabels().size < 40)
    }

    @Test
    fun `a budget that ends on a caller read still reports the callers that were not read`() {
        for (budget in 1..40) {
            val diagram = walked(chainWorld(300), "app.f0299", budget)
            assertTrue(diagram.omitted.notExpanded > 0, "budget $budget")
        }
    }

    @Test
    fun `a budget that ends on a callee read still reports the callees that were not read`() {
        for (budget in 1..40) {
            val diagram = walked(chainWorld(300), "app.f0000", budget)
            assertTrue(diagram.omitted.notExpanded > 0, "budget $budget")
        }
    }

    @Test
    fun `a type diagram that runs out of budget on an incoming read reports what it did not read`() {
        for (budget in 1..40) {
            val diagram = walked(typeChain(100), "app.T099", budget)
            assertTrue(diagram.omitted.notExpanded > 0, "budget $budget")
        }
    }

    @Test
    fun `a type with more members than the member limit is reported as not expanded`() {
        val world = SymbolWorld()
        val big = world.type("Big")
        repeat(10_001) { world.function("m$it", big) }
        val graph = SymbolGraph(world)
        val diagram = graph.diagram("app.Big", depth = 0)
        assertEquals(1, diagram.omitted.notExpanded)
        assertEquals(1, diagram.nodes.size)
        assertTrue(diagram.edges.isEmpty())
        assertEquals(0, graph.count("outgoing:"))
        assertEquals(0, graph.count("forbidden"))
    }
}
