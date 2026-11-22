package io.github.krosstack.kscope.diagram

import io.github.krosstack.kscope.diagram.build.symbol.SymbolGraph
import io.github.krosstack.kscope.diagram.build.symbol.SymbolWorld
import io.github.krosstack.kscope.diagram.build.symbol.callFlowWorld
import io.github.krosstack.kscope.diagram.build.symbol.diagram
import io.github.krosstack.kscope.diagram.build.symbol.typeFlowWorld
import io.github.krosstack.kscope.diagram.emit.DiagramJson
import io.github.krosstack.kscope.diagram.emit.DotEmitter
import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class ShuffleInvarianceTest {
    @Test
    fun `input permutations preserve bytes in every format including cut diagrams`() {
        for (kind in listOf(DiagramKind.MODULES, DiagramKind.COMPONENTS)) {
            for (links in listOf(0, 3, 300)) {
                val request = DiagramRequest(kind, links = links)
                val diagrams = (0..2).map {
                    (DiagramBuilder(DiagramGraph(it)).build(request) as DiagramResult.Built).diagram
                }
                assertEquals(1, diagrams.map { MermaidEmitter.render(it) }.distinct().size)
                assertEquals(1, diagrams.map { DotEmitter.render(it) }.distinct().size)
                assertEquals(1, diagrams.map { DiagramJson.render(it) }.distinct().size)
            }
        }
    }

    @Test
    fun `the order a reader returns never changes the bytes of a symbol diagram`() {
        val subjects = listOf(callFlowWorld() to "app.Checkout.pay", typeFlowWorld() to "app.Checkout")
        for ((world, node) in subjects) {
            for (depth in listOf(0, 1, 2)) assertStable(world, node, depth)
        }
    }

    private fun assertStable(world: SymbolWorld, node: String, depth: Int) {
        for (links in listOf(0, 3, 300)) {
            val diagrams = (0..2).map { SymbolGraph(world, it).diagram(node, depth, links) }
            assertEquals(1, diagrams.map { MermaidEmitter.render(it) }.distinct().size)
            assertEquals(1, diagrams.map { DotEmitter.render(it) }.distinct().size)
            assertEquals(1, diagrams.map { DiagramJson.render(it) }.distinct().size)
        }
    }
}
