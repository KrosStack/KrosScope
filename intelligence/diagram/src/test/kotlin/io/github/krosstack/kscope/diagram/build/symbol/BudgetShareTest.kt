package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramKind
import io.github.krosstack.kscope.diagram.DiagramRequest
import io.github.krosstack.kscope.diagram.DiagramResult
import io.github.krosstack.kscope.model.SymbolId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class BudgetShareTest {
    private fun drawn(graph: SymbolGraph, node: String, budget: Int, depth: Int = 1): Diagram {
        val request = DiagramRequest(DiagramKind.SYMBOL, node, depth, Int.MAX_VALUE, Int.MAX_VALUE)
        val result = SymbolDiagramBuilder(graph, budget).build(request) { DiagramResult.Built(it) }
        return (result as DiagramResult.Built).diagram
    }

    private fun reads(graph: SymbolGraph, world: SymbolWorld, direction: String): List<String> =
        graph.log.filter { it.startsWith("$direction:") }
            .map { world.symbols.getValue(SymbolId(it.substringAfter(':'))).name }

    private fun queries(graph: SymbolGraph): Int = graph.log.count { it.startsWith("symbols:") && it.contains('#') }

    private fun plainHub(members: Int): SymbolWorld {
        val world = SymbolWorld()
        val hub = world.type("Hub")
        repeat(members) { world.function("m${it.toString().padStart(2, '0')}", hub) }
        return world
    }

    @Test
    fun `with a spare of zero to three the two directions share it and nothing goes over the budget`() {
        val expected = mapOf(
            1 to (emptyList<String>() to emptyList()),
            2 to (listOf("Hub") to emptyList()),
            3 to (listOf("Hub") to listOf("Hub")),
            4 to (listOf("Hub") to listOf("Hub", "m00")),
        )
        for ((budget, reading) in expected) {
            val world = plainHub(6)
            val graph = SymbolGraph(world)
            drawn(graph, "app.Hub", budget)
            assertEquals(reading.first, reads(graph, world, "outgoing"), "outgoing at budget $budget")
            assertEquals(reading.second, reads(graph, world, "incoming"), "incoming at budget $budget")
            val used = queries(graph) + graph.count("outgoing:") + graph.count("incoming:")
            assertTrue(used <= budget, "budget $budget used $used")
        }
    }

    @Test
    fun `an even split leaves each direction the same number of members`() {
        val world = plainHub(6)
        val graph = SymbolGraph(world)
        drawn(graph, "app.Hub", budget = 5)
        assertEquals(listOf("Hub", "m00"), reads(graph, world, "outgoing"))
        assertEquals(listOf("Hub", "m00"), reads(graph, world, "incoming"))
    }

    @Test
    fun `a caller of a member beyond half the spare is drawn when the incoming share reaches it`() {
        val world = plainHub(12)
        val member = world.symbols.values.first { it.name == "m03" }.id
        world.call(world.function("run", world.type("Caller")), member, 1)
        val graph = SymbolGraph(world)
        val diagram = drawn(graph, "app.Hub", budget = 10)
        assertEquals(listOf("Hub", "m00", "m01", "m02"), reads(graph, world, "outgoing"))
        assertEquals(setOf("app.Hub", "app.Caller"), diagram.nodeLabels())
        assertEquals(listOf("app.Caller -> app.Hub [depends]"), diagram.edgeLines())
    }

    @Test
    fun `a neighbour that is only followed downstream reads all its members and no incoming relations`() {
        val world = SymbolWorld()
        val start = world.function("go", world.type("Start"))
        val bee = world.type("Bee")
        val members = (0 until 60).map { world.function("b${it.toString().padStart(2, '0')}", bee) }
        world.call(start, members.first(), 1)
        world.call(members.last(), world.function("run", world.type("End")), 2)
        val graph = SymbolGraph(world)
        val diagram = drawn(graph, "app.Start", budget = 140, depth = 2)
        assertEquals(setOf("app.Start", "app.Bee", "app.End"), diagram.nodeLabels())
        assertEquals(0, diagram.omitted.membersNotRead)
        assertEquals(0, graph.log.count { it.startsWith("incoming:") && it.contains("Bee#") })
    }

    @Test
    fun `the types behind a hub with thousands of members still get their reads`() {
        val world = SymbolWorld()
        val big = world.type("Big")
        val members = (0 until 300).map { world.function("m${it.toString().padStart(3, '0')}", big) }
        val second = world.function("run", world.type("Two"))
        val third = world.function("run", world.type("Three"))
        val first = world.function("run", world.type("One"))
        world.call(members.first(), first, 1)
        world.call(first, second, 2)
        world.call(first, third, 3)
        world.call(second, world.function("run", world.type("Four")), 4)
        world.call(world.function("run", world.type("Cal")), members[10], 5)
        val diagram = drawn(SymbolGraph(world), "app.Big", budget = 500, depth = 0)
        val expected = setOf("app.Big", "app.Cal", "app.One", "app.Two", "app.Three", "app.Four")
        assertEquals(expected, diagram.nodeLabels())
    }
}
