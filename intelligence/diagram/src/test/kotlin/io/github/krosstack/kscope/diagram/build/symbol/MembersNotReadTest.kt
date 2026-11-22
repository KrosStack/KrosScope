package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramKind
import io.github.krosstack.kscope.diagram.DiagramRequest
import io.github.krosstack.kscope.diagram.DiagramResult
import io.github.krosstack.kscope.diagram.emit.DiagramJson
import io.github.krosstack.kscope.diagram.emit.DotEmitter
import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import io.github.krosstack.kscope.spi.graph.GraphReader
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class MembersNotReadTest {
    private fun drawn(reader: GraphReader, node: String, budget: Int): Diagram {
        val request = DiagramRequest(DiagramKind.SYMBOL, node, 1, Int.MAX_VALUE, Int.MAX_VALUE)
        val result = SymbolDiagramBuilder(reader, budget).build(request) { DiagramResult.Built(it) }
        return assertIs<DiagramResult.Built>(result).diagram
    }

    @Test
    fun `a type with more members than the budget can read reports how many members it left unread`() {
        val diagram = drawn(SymbolGraph(wideWorld(30)), "app.Wide", budget = 20)
        assertEquals(34, diagram.omitted.membersNotRead)
        assertTrue(diagram.omitted.notExpanded > 0)
    }

    @Test
    fun `the members that were read still draw their neighbours`() {
        val diagram = drawn(SymbolGraph(wideWorld(30)), "app.Wide", budget = 20)
        val expected = (0 until 8).map { "app.T${it.toString().padStart(2, '0')}" }.toSet()
        assertEquals(setOf("app.Wide") + expected, diagram.nodeLabels())
        assertEquals(8, diagram.edges.size)
    }

    @Test
    fun `all three outputs print the members that were not read`() {
        val diagram = drawn(SymbolGraph(wideWorld(30)), "app.Wide", budget = 20)
        val text = "; 34 members not read: the read limit left their links unread"
        assertTrue(MermaidEmitter.render(diagram).contains(text))
        assertTrue(DotEmitter.render(diagram).contains(text))
        assertTrue(DiagramJson.render(diagram).contains("\"membersNotRead\": 34"))
    }

    @Test
    fun `a type that fits its budget prints no count of members not read`() {
        val diagram = drawn(SymbolGraph(wideWorld(30)), "app.Wide", budget = 1_200)
        assertEquals(0, diagram.omitted.membersNotRead)
        assertEquals(0, diagram.omitted.notExpanded)
        assertFalse(MermaidEmitter.render(diagram).contains("members not read"))
        assertFalse(DotEmitter.render(diagram).contains("members not read"))
        assertFalse(DiagramJson.render(diagram).contains("membersNotRead"))
    }

    @Test
    fun `the members below the direct members are looked up only while budget remains`() {
        val world = SymbolWorld()
        val host = world.type("Host")
        val outer = world.function("outer", host)
        val inner = world.function("inner", outer)
        world.call(inner, world.function("run", world.type("Target")), 1)
        val full = drawn(SymbolGraph(world), "app.Host", budget = 1_200)
        assertEquals(listOf("app.Host -> app.Target [depends]"), full.edgeLines())
        val short = drawn(SymbolGraph(world), "app.Host", budget = 2)
        assertTrue(short.edges.isEmpty())
        assertEquals(1, short.omitted.membersNotRead)
        assertTrue(short.omitted.notExpanded > 0)
    }

    @Test
    fun `the number of members not read does not depend on the order the reader returns`() {
        val diagrams = (0..2).map { SymbolGraph(wideWorld(30), it).cutDiagram("app.Wide", budget = 20) }
        assertEquals(1, diagrams.map { MermaidEmitter.render(it) }.distinct().size)
        assertEquals(1, diagrams.map { DotEmitter.render(it) }.distinct().size)
        assertEquals(1, diagrams.map { DiagramJson.render(it) }.distinct().size)
    }

    @Test
    fun `a type with thousands of members does not spend the budget on container queries`() {
        val world = SymbolWorld()
        val big = world.type("Big")
        val target = world.function("run", world.type("Target"))
        repeat(3_000) { world.call(world.function("m${it.toString().padStart(4, '0')}", big), target, it + 1) }
        val graph = SymbolGraph(world)
        val diagram = drawn(graph, "app.Big", budget = 1_200)
        assertEquals(1, graph.log.count { it.startsWith("symbols:") && it.contains("Big#") })
        assertEquals(setOf("app.Big", "app.Target"), diagram.nodeLabels())
        assertEquals(3_000 - 299, diagram.omitted.membersNotRead)
        assertTrue(graph.log.size <= 1_200 + 6)
    }

    @Test
    fun `a type with thousands of members still reads the incoming relationships of its first members`() {
        val world = SymbolWorld()
        val big = world.type("Big")
        val members = (0 until 3_000).map { world.function("m${it.toString().padStart(4, '0')}", big) }
        world.call(world.function("run", world.type("Caller")), members[1], 1)
        val diagram = drawn(SymbolGraph(world), "app.Big", budget = 1_200)
        assertEquals(setOf("app.Big", "app.Caller"), diagram.nodeLabels())
        assertEquals(listOf("app.Caller -> app.Big [depends]"), diagram.edgeLines())
        assertTrue(diagram.omitted.membersNotRead > 0)
    }
}
