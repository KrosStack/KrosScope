package io.github.krosstack.kscope.diagram.build.symbol

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class BoundedReadingTest {
    private fun hub(callers: Int): SymbolWorld {
        val world = SymbolWorld()
        val target = world.function("hub")
        for (number in 0 until callers) {
            world.call(world.function("c${number.toString().padStart(3, '0')}"), target, number + 1)
        }
        return world
    }

    @Test
    fun `neither the call flow nor the type diagram issues a whole graph query`() {
        val calls = SymbolGraph(callFlowWorld())
        calls.diagram("app.Checkout.pay", depth = 0)
        val types = SymbolGraph(typeFlowWorld())
        types.diagram("app.Checkout", depth = 0)
        assertEquals(0, calls.count("forbidden"))
        assertEquals(0, types.count("forbidden"))
    }

    @Test
    fun `no node is read twice`() {
        val calls = SymbolGraph(callFlowWorld())
        calls.diagram("app.Checkout.pay", depth = 0)
        val types = SymbolGraph(typeFlowWorld())
        types.diagram("app.Checkout", depth = 0)
        assertEquals(calls.log.size, calls.log.distinct().size)
        assertEquals(types.log.size, types.log.distinct().size)
    }

    @Test
    fun `a chain longer than the cap is read only as far as the links can draw`() {
        val graph = SymbolGraph(chainWorld(50))
        val diagram = graph.diagram("app.f0000", depth = 0, links = 5)
        assertEquals(6, diagram.nodeLabels().size)
        assertEquals(5, diagram.edges.size)
        assertEquals(1, diagram.omitted.notExpanded)
        assertEquals(6, graph.count("outgoing:"))
        assertEquals(1, graph.count("incoming:"))
        assertTrue(graph.count("symbol:") <= 8)
        assertEquals(0, graph.count("forbidden"))
    }

    @Test
    fun `a hub with more callers than the cap reports the others and leaves out the call positions of the unread`() {
        val graph = SymbolGraph(hub(100))
        val diagram = graph.diagram("app.hub", depth = 0, links = 10)
        assertEquals(11, diagram.nodeLabels().size)
        assertEquals(10, diagram.edges.size)
        assertTrue(diagram.edges.all { it.position == null })
        assertEquals(100, diagram.omitted.notExpanded)
        assertEquals(1, graph.count("outgoing:"))
        assertTrue(graph.count("symbol:") <= 13)
        val uncapped = SymbolGraph(hub(100)).diagram("app.hub", depth = 0)
        assertTrue(uncapped.edges.all { it.position != null })
    }

    @Test
    fun `a walk that fits its cap reports nothing as not expanded`() {
        val diagram = SymbolGraph(callFlowWorld()).diagram("app.Checkout.pay", depth = 0)
        assertEquals(0, diagram.omitted.notExpanded)
    }

    @Test
    fun `members are found by one container query per type`() {
        val graph = SymbolGraph(typeFlowWorld())
        graph.diagram("app.Checkout", depth = 1)
        val containers = graph.log.filter { it.startsWith("symbols:kscope") }
        assertTrue(containers.isNotEmpty())
        assertEquals(containers.size, containers.distinct().size)
        assertNull(graph.log.firstOrNull { it == "forbidden:symbols" })
    }
}
