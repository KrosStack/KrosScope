package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramBuilder
import io.github.krosstack.kscope.diagram.DiagramEdge
import io.github.krosstack.kscope.diagram.DiagramKind
import io.github.krosstack.kscope.diagram.DiagramRequest
import io.github.krosstack.kscope.diagram.DiagramResult
import io.github.krosstack.kscope.diagram.Omission
import io.github.krosstack.kscope.diagram.build.DiagramCut
import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.graph.GraphReader
import kotlin.test.assertIs

internal fun GraphReader.result(node: String?, depth: Int = 1, links: Int = 300, characters: Int = 40_000) =
    DiagramBuilder(this).build(DiagramRequest(DiagramKind.SYMBOL, node, depth, links, characters))

internal fun GraphReader.diagram(node: String, depth: Int = 1, links: Int = 300): Diagram =
    assertIs<DiagramResult.Built>(result(node, depth, links)).diagram

internal fun Diagram.edgeLines(): List<String> {
    val names = nodes.associate { it.id to it.label } + reasons.associate { it.id to it.reason }
    return edges.map { edge ->
        val kind = edge.label.substringBefore(';')
        val number = edge.position?.let { " #$it" }.orEmpty()
        "${names.getValue(edge.from)} -> ${names.getValue(edge.to)} [$kind$number]"
    }.sorted()
}

internal fun Diagram.nodeLabels(): Set<String> = nodes.filter { it.kind != "aggregate" }.map { it.label }.toSet()

internal fun Diagram.edgesBetween(from: String, to: String): List<DiagramEdge> {
    val ids = nodes.associate { it.label to it.id }
    return edges.filter { it.from == ids[from] && it.to == ids[to] }
}

internal fun GraphReader.cutDiagram(node: String, budget: Int, links: Int = Int.MAX_VALUE - 1): Diagram {
    val request = DiagramRequest(DiagramKind.SYMBOL, node, 1, links, Int.MAX_VALUE)
    val result = SymbolDiagramBuilder(this, budget).build(request) {
        DiagramCut().apply(it, request) { diagram -> MermaidEmitter.render(diagram).length }
    }
    return assertIs<DiagramResult.Built>(result).diagram
}

internal fun Diagram.reasons(): Omission = Omission(
    0,
    emptyMap(),
    notInType = omitted.notInType,
    notCallable = omitted.notCallable,
    withoutSymbol = omitted.withoutSymbol,
    withinType = omitted.withinType,
    beyondDepth = omitted.beyondDepth,
    stopped = omitted.stopped,
)

internal fun hubWithMember(): Pair<SymbolWorld, SymbolId> {
    val world = SymbolWorld()
    return world to world.function("m", world.type("Hub"))
}
