package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.emit.DiagramJson
import io.github.krosstack.kscope.diagram.emit.DotEmitter
import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class UndrawnCallsTest {
    private val graph = SymbolGraph(constructionWorld())
    private val build = "app.Factory.build"

    @Test
    fun `a call that resolves to a class or a property is not drawn`() {
        val diagram = graph.diagram(build, depth = 1)
        assertEquals(setOf("fun build()", "fun make()"), diagram.nodeLabels())
        assertEquals(listOf("fun build() -> fun make() [calls #3]"), diagram.edgeLines())
    }

    @Test
    fun `those calls are counted by the kind of their target`() {
        val diagram = graph.diagram(build, depth = 1)
        assertEquals(mapOf("kotlin.class" to 1, "kotlin.property" to 1), diagram.omitted.callsNotDrawn)
    }

    @Test
    fun `every call row counts once`() {
        val world = constructionWorld()
        val ids = world.symbols.values.associate { it.qualifiedName to it.id }
        val widget = ids.getValue("app.Widget")
        repeat(2) { world.call(ids.getValue("app.Factory.build"), widget, 20 + it) }
        val diagram = SymbolGraph(world).diagram(build, depth = 1)
        assertEquals(mapOf("kotlin.class" to 3, "kotlin.property" to 1), diagram.omitted.callsNotDrawn)
    }

    @Test
    fun `all three outputs print the calls that were not drawn`() {
        val diagram = graph.diagram(build, depth = 1)
        val text = "calls to targets that are no callable, not drawn, by target kind: kotlin.class=1, kotlin.property=1"
        assertTrue(MermaidEmitter.render(diagram).contains(text))
        assertTrue(DotEmitter.render(diagram).contains(text))
        val json = DiagramJson.render(diagram)
        assertTrue(json.contains("\"callsNotDrawn\""))
        assertTrue(json.contains("\"kotlin.class\": 1"))
        assertTrue(json.contains("\"kotlin.property\": 1"))
    }

    @Test
    fun `a diagram without such calls prints nothing about them`() {
        val diagram = SymbolGraph(callFlowWorld()).diagram("app.Checkout.pay", depth = 0)
        assertTrue(diagram.omitted.callsNotDrawn.isEmpty())
        assertFalse(MermaidEmitter.render(diagram).contains("no callable, not drawn"))
        assertFalse(DotEmitter.render(diagram).contains("no callable, not drawn"))
        assertFalse(DiagramJson.render(diagram).contains("callsNotDrawn"))
    }

    @Test
    fun `a type diagram draws calls to any kind of member and reports none`() {
        val diagram = SymbolGraph(constructionWorld()).diagram("app.Factory", depth = 1)
        assertTrue(diagram.omitted.callsNotDrawn.isEmpty())
        assertTrue("app.Factory -> app.Widget [depends]" in diagram.edgeLines())
    }

    @Test
    fun `the counts do not depend on the order the reader returns`() {
        val diagrams = (0..2).map { SymbolGraph(constructionWorld(), it).diagram(build, depth = 1) }
        assertEquals(1, diagrams.map { MermaidEmitter.render(it) }.distinct().size)
        assertEquals(1, diagrams.map { DotEmitter.render(it) }.distinct().size)
        assertEquals(1, diagrams.map { DiagramJson.render(it) }.distinct().size)
    }

    @Test
    fun `a class that is already known is counted and takes no slot`() {
        val world = SymbolWorld()
        val source = world.function("s")
        val target = world.type("W")
        val other = world.function("x")
        world.call(source, target, 1)
        world.call(source, other, 2)
        world.call(other, target, 3)
        val diagram = SymbolGraph(world).diagram("app.s", depth = 0, links = 1)
        assertEquals(setOf("fun s()", "fun x()"), diagram.nodeLabels())
        assertEquals(0, diagram.omitted.notExpanded)
        assertEquals(mapOf("kotlin.class" to 2), diagram.omitted.callsNotDrawn)
    }

    @Test
    fun `only the calls of callables whose calls were followed are counted`() {
        val world = SymbolWorld()
        val source = world.function("s")
        val first = world.type("W1")
        val other = world.function("x")
        world.call(source, first, 1)
        world.call(source, other, 2)
        world.call(other, first, 3)
        world.call(other, world.type("W2"), 4)
        val diagrams = (0..2).map { SymbolGraph(world, it).diagram("app.s", depth = 1) }
        diagrams.forEach { assertEquals(mapOf("kotlin.class" to 1), it.omitted.callsNotDrawn) }
        assertEquals(1, diagrams.map { DiagramJson.render(it) }.distinct().size)
    }
}
