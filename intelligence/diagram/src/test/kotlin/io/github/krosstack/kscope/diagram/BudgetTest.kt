package io.github.krosstack.kscope.diagram

import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class BudgetTest {
    @Test
    fun `link budgets count drawn edges and account for every omitted fact per state`() {
        val builder = DiagramBuilder(DiagramGraph())
        val full = (builder.build(DiagramRequest(DiagramKind.MODULES)) as DiagramResult.Built).diagram
        val cut = (builder.build(DiagramRequest(DiagramKind.MODULES, links = 4)) as DiagramResult.Built).diagram
        assertEquals(4, cut.edges.size)
        DrawnState.entries.forEach { state ->
            assertEquals(
                full.edges.sumOf { it.byState[state] ?: 0 },
                cut.edges.sumOf { it.byState[state] ?: 0 } + cut.omitted.linksByState.getValue(state),
            )
            val stateLabel = state.name.lowercase().replace('_', '-')
            assertTrue(MermaidEmitter.render(cut).contains("$stateLabel=${cut.omitted.linksByState[state]}"))
        }
    }

    @Test
    fun `character budgets measure the complete serialized mermaid`() {
        val builder = DiagramBuilder(DiagramGraph())
        val full = (builder.build(DiagramRequest(DiagramKind.MODULES)) as DiagramResult.Built).diagram
        val floor = (builder.build(DiagramRequest(DiagramKind.MODULES, links = 0)) as DiagramResult.Built).diagram
        val budget = (MermaidEmitter.render(full).length + MermaidEmitter.render(floor).length) / 2
        val cut = (
            builder.build(
                DiagramRequest(DiagramKind.MODULES, characters = budget),
            ) as DiagramResult.Built
            ).diagram
        assertTrue(MermaidEmitter.render(cut).length <= budget)
        assertTrue(cut.edges.size < full.edges.size)
    }

    @Test
    fun `depth scopes include neighbours and reject missing roots`() {
        val builder = DiagramBuilder(DiagramGraph())
        val root = (
            builder.build(
                DiagramRequest(DiagramKind.MODULES, node = ":module0", depth = 0),
            ) as DiagramResult.Built
            ).diagram
        val neighbours = (
            builder.build(
                DiagramRequest(DiagramKind.MODULES, node = ":module0", depth = 1),
            ) as DiagramResult.Built
            ).diagram
        assertEquals(listOf(":module0"), root.nodes.map { it.label })
        assertEquals(setOf(":module0", ":module1", ":module5"), neighbours.nodes.map { it.label }.toSet())
        assertFailsWith<IllegalArgumentException> {
            builder.build(DiagramRequest(DiagramKind.MODULES, node = "absent"))
        }
    }

    @Test
    fun `shared reason codes do not expand a depth scope`() {
        val diagram = (
            DiagramBuilder(DiagramGraph()).build(
                DiagramRequest(DiagramKind.MODULES, node = ":module4", depth = 2),
            ) as DiagramResult.Built
            ).diagram
        assertEquals(setOf(":module4", ":module9"), diagram.nodes.map { it.label }.toSet())
    }
}
