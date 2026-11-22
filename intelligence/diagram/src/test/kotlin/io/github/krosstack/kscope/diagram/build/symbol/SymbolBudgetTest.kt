package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.DiagramResult
import io.github.krosstack.kscope.diagram.DrawnState
import io.github.krosstack.kscope.diagram.emit.DiagramJson
import io.github.krosstack.kscope.diagram.emit.DotEmitter
import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class SymbolBudgetTest {
    private val graph = SymbolGraph(callFlowWorld())
    private val pay = "app.Checkout.pay"

    @Test
    fun `a link budget keeps the edges nearest to the selected callable and reports the rest`() {
        val cut = graph.diagram(pay, depth = 0, links = 4)
        assertEquals(
            listOf(
                "fun pay() -> fun record() [calls #2]",
                "fun pay() -> fun validate() [calls #1]",
                "fun pay() -> jni.native-side-not-analysed [calls #5]",
                "fun pay() -> kotlin.unresolved-reference [calls #4]",
            ),
            cut.edgeLines(),
        )
        assertEquals(
            mapOf(
                DrawnState.EXACT to 3,
                DrawnState.PROBABLE to 0,
                DrawnState.HEURISTIC to 0,
                DrawnState.UNRESOLVED to 0,
                DrawnState.NOT_ANALYSED to 0,
            ),
            cut.omitted.linksByState,
        )
        assertEquals(2, cut.omitted.nodes)
        assertTrue(cut.title.contains("4 of 8 links"))
        assertTrue(MermaidEmitter.render(cut).contains("exact=3"))
    }

    @Test
    fun `a character budget drops the farthest edges first`() {
        val full = graph.diagram(pay, depth = 0)
        val floor = graph.diagram(pay, depth = 0, links = 0)
        val budget = (MermaidEmitter.render(full).length + MermaidEmitter.render(floor).length) / 2
        val cut = assertIs<DiagramResult.Built>(graph.result(pay, depth = 0, characters = budget)).diagram
        assertTrue(MermaidEmitter.render(cut).length <= budget)
        assertTrue(cut.edges.size < full.edges.size)
        assertTrue("fun entry() -> fun other() [calls #1]" !in cut.edgeLines())
        assertTrue("fun pay() -> fun validate() [calls #1]" in cut.edgeLines())
    }

    @Test
    fun `no links leave the selected callable alone with the states of what is left out`() {
        val floor = graph.diagram(pay, depth = 0, links = 0)
        assertEquals(
            setOf("fun pay()", "unresolved", "not_analysed"),
            floor.nodes.map { it.label.substringBefore(':') }.toSet(),
        )
        assertEquals(listOf("fun pay()"), floor.nodes.filter { it.selected }.map { it.label })
        assertTrue(floor.edges.isEmpty())
        assertEquals(3, floor.omitted.notExpanded)
    }

    @Test
    fun `the selected callable is marked in every output when no link is drawn`() {
        val floor = graph.diagram(pay, depth = 0, links = 0)
        assertTrue(MermaidEmitter.render(floor).contains(":::selected"))
        assertTrue(MermaidEmitter.render(floor).contains("classDef selected"))
        assertTrue(DotEmitter.render(floor).contains("fillcolor=gold"))
        assertTrue(DiagramJson.render(floor).contains("\"selected\": true"))
    }

    @Test
    fun `a character budget that drops every edge still draws the selected callable`() {
        val minimum = assertIs<DiagramResult.BudgetTooSmall>(graph.result(pay, depth = 0, characters = 1))
        val built = graph.result(pay, depth = 0, characters = minimum.minimumCharacters)
        val cut = assertIs<DiagramResult.Built>(built).diagram
        assertTrue(cut.edges.isEmpty())
        assertEquals(listOf("fun pay()"), cut.nodes.filter { it.selected }.map { it.label })
    }

    @Test
    fun `the selected type is drawn when the link budget is zero`() {
        val floor = SymbolGraph(typeFlowWorld()).diagram("app.Checkout", depth = 0, links = 0)
        assertEquals(listOf("app.Checkout"), floor.nodes.filter { it.selected }.map { it.label })
    }
}
