package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramKind
import io.github.krosstack.kscope.diagram.DiagramRequest
import io.github.krosstack.kscope.diagram.DiagramResult
import io.github.krosstack.kscope.diagram.emit.DiagramJson
import io.github.krosstack.kscope.diagram.emit.DotEmitter
import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import io.github.krosstack.kscope.model.SymbolCategory
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class NeighbourRollUpTest {
    private val cap = 20

    private fun drawn(graph: SymbolGraph, budget: Int, depth: Int = 1): Diagram {
        val request = DiagramRequest(DiagramKind.SYMBOL, "app.Hub", depth, cap, Int.MAX_VALUE)
        val result = SymbolDiagramBuilder(graph, budget).build(request) { DiagramResult.Built(it) }
        return assertIs<DiagramResult.Built>(result).diagram
    }

    private fun neighbours(count: Int): Set<String> =
        (0 until count).map { "app.S${it.toString().padStart(3, '0')}" }.toSet()

    @Test
    fun `a central type draws neighbour types up to the node cap when the budget cannot read one source each`() {
        val world = hubWorld(150)
        val graph = SymbolGraph(world)
        val diagram = drawn(graph, budget = 40)
        assertEquals(setOf("app.Hub") + neighbours(cap), diagram.nodeLabels())
        assertEquals(cap, diagram.edges.size)
        assertTrue(diagram.edgeLines().all { it.endsWith("-> app.Hub [depends]") })
        val callables = world.symbols.values.filter { it.kind.category == SymbolCategory.CALLABLE }
            .map { "symbol:${it.id.value}" }.toSet()
        assertTrue(graph.log.none { it in callables })
        assertTrue(graph.count("symbol:") <= 2 + cap)
    }

    @Test
    fun `the neighbours are the first ones in canonical order whatever order the reader returns`() {
        val world = hubWorld(150)
        val diagrams = (0..2).map { SymbolGraph(world, it).cutDiagram("app.Hub", budget = 40, links = cap) }
        diagrams.forEach { assertEquals(setOf("app.Hub") + neighbours(cap), it.nodeLabels()) }
        assertEquals(1, diagrams.map { MermaidEmitter.render(it) }.distinct().size)
        assertEquals(1, diagrams.map { DotEmitter.render(it) }.distinct().size)
        assertEquals(1, diagrams.map { DiagramJson.render(it) }.distinct().size)
    }

    @Test
    fun `a caller declared at the top level has no type and costs no symbol read`() {
        val world = SymbolWorld()
        val member = world.function("m", world.type("Hub"))
        repeat(50) { world.call(world.function("t$it"), member, it + 1) }
        val graph = SymbolGraph(world)
        val diagram = drawn(graph, budget = 40)
        assertEquals(setOf("app.Hub"), diagram.nodeLabels())
        assertTrue(diagram.edges.isEmpty())
        assertEquals(2, graph.count("symbol:"))
    }

    @Test
    fun `a caller in a file facade that no declaration backs draws nothing`() {
        val world = SymbolWorld()
        val member = world.function("m", world.type("Hub"))
        val helper = world.facadeFunction("helper", "UtilKt")
        world.call(helper, member, 1)
        val graph = SymbolGraph(world)
        val diagram = drawn(graph, budget = 40)
        assertEquals(setOf("app.Hub"), diagram.nodeLabels())
        assertTrue(diagram.edges.isEmpty())
        assertEquals(0, graph.count("symbol:${helper.value}"))
        assertEquals(1, graph.log.count { it.startsWith("symbol:") && it.contains("UtilKt#") })
    }

    @Test
    fun `an id that does not name its type falls back to the container of the symbol`() {
        val world = SymbolWorld()
        val hub = world.type("Hub")
        val member = world.function("m", hub)
        val legacy = world.type("Legacy")
        val caller = world.opaqueFunction("run", legacy)
        world.call(caller, member, 1)
        val graph = SymbolGraph(world)
        val diagram = drawn(graph, budget = 40)
        assertEquals(setOf("app.Hub", "app.Legacy"), diagram.nodeLabels())
        assertEquals(listOf("app.Legacy -> app.Hub [depends]"), diagram.edgeLines())
        assertEquals(1, graph.count("symbol:${caller.value}"))
    }

    @Test
    fun `a legacy id without a container draws nothing`() {
        val world = SymbolWorld()
        val member = world.function("m", world.type("Hub"))
        val orphan = world.opaqueFunction("run", world.type("Legacy"))
        world.symbols[orphan] = world.symbols.getValue(orphan).copy(container = null)
        world.call(orphan, member, 1)
        val diagram = drawn(SymbolGraph(world), budget = 40)
        assertEquals(setOf("app.Hub"), diagram.nodeLabels())
    }

    @Test
    fun `file facades that sort before the neighbours take no slot and no budget`() {
        val plain = drawn(SymbolGraph(hubWorld(150)), budget = 40)
        val world = hubWorld(150)
        val member = world.symbols.values.first { it.name == "m0" }.id
        repeat(30) {
            val facade = "A${it.toString().padStart(2, '0')}Kt"
            world.call(world.facadeFunction("helper$it", facade), member, 100 + it)
        }
        val graph = SymbolGraph(world)
        val diagram = drawn(graph, budget = 40)
        assertEquals(setOf("app.Hub") + neighbours(cap), diagram.nodeLabels())
        assertEquals(plain.omitted.copy(notInType = 30), diagram.omitted)
        assertEquals(plain.edges.size, diagram.edges.size)
        assertEquals(0, graph.log.count { it.startsWith("outgoing:") && it.contains("Kt#") })
        assertEquals(0, graph.log.count { it.startsWith("incoming:") && it.contains("Kt#") })
    }

    @Test
    fun `the cap takes downstream and upstream neighbours together in canonical order`() {
        val world = SymbolWorld()
        val hub = world.type("Hub")
        val member = world.function("m", hub)
        val caller = world.function("run", world.type("A000"))
        world.call(caller, member, 1)
        repeat(30) {
            val target = world.function("run", world.type("D${it.toString().padStart(3, '0')}"))
            world.call(member, target, it + 2)
        }
        val diagrams = (0..2).map { SymbolGraph(world, it).cutDiagram("app.Hub", budget = 200, links = cap) }
        val downstream = (0 until cap - 1).map { "app.D${it.toString().padStart(3, '0')}" }
        val expected = setOf("app.Hub", "app.A000") + downstream
        diagrams.forEach { assertEquals(expected, it.nodeLabels()) }
        assertEquals(1, diagrams.map { MermaidEmitter.render(it) }.distinct().size)
    }
}
