package io.github.krosstack.kscope.diagram

import io.github.krosstack.kscope.diagram.emit.DiagramJson
import io.github.krosstack.kscope.diagram.emit.DotEmitter
import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class ShuffleInvarianceTest {
    @Test
    fun `input permutations preserve bytes in every format including cut diagrams`() {
        for (kind in DiagramKind.entries) {
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
}
