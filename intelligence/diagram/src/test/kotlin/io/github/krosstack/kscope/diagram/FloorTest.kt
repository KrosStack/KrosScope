package io.github.krosstack.kscope.diagram

import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class FloorTest {
    @Test
    fun `zero drawn links preserve every present state as an aggregate`() {
        for (kind in DiagramKind.entries) {
            val floor = (
                DiagramBuilder(DiagramGraph()).build(
                    DiagramRequest(kind, links = 0),
                ) as DiagramResult.Built
                ).diagram
            assertEquals(
                DrawnState.entries.map { it.name.lowercase() }.toSet(),
                floor.nodes.map { it.label.substringBefore(':') }.toSet(),
            )
        }
    }

    @Test
    fun `the floor matches its golden`() {
        val floor = (
            DiagramBuilder(DiagramGraph()).build(
                DiagramRequest(DiagramKind.MODULES, links = 0),
            ) as DiagramResult.Built
            ).diagram
        assertEquals(requireNotNull(javaClass.getResource("/floor.mmd")).readText(), MermaidEmitter.render(floor))
    }

    @Test
    fun `the exact character floor succeeds`() {
        val builder = DiagramBuilder(DiagramGraph())
        val floor = builder.build(DiagramRequest(DiagramKind.MODULES, links = 0)) as DiagramResult.Built
        val minimum = MermaidEmitter.render(floor.diagram).length
        assertEquals(floor, builder.build(DiagramRequest(DiagramKind.MODULES, links = 0, characters = minimum)))
    }

    @Test
    fun `one character below the floor returns the minimum`() {
        val builder = DiagramBuilder(DiagramGraph())
        val floor = builder.build(DiagramRequest(DiagramKind.MODULES, links = 0)) as DiagramResult.Built
        val minimum = MermaidEmitter.render(floor.diagram).length
        assertEquals(
            DiagramResult.BudgetTooSmall(minimum),
            builder.build(DiagramRequest(DiagramKind.MODULES, characters = minimum - 1)),
        )
    }
}
