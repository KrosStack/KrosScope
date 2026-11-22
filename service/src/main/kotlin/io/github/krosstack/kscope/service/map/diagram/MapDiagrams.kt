package io.github.krosstack.kscope.service.map.diagram

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramBuilder
import io.github.krosstack.kscope.diagram.DiagramKind
import io.github.krosstack.kscope.diagram.DiagramRequest
import io.github.krosstack.kscope.diagram.DiagramResult
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.map.WorkspaceMap
import io.github.krosstack.kscope.service.map.tree.NONE

/**
 * The diagram of a symbol of [map] within [depth] steps and [links] lines. A null [depth] walks as far as the budgets
 * of the diagram let it. A question that has no answer returns a value with [MapDiagram.error]. Runs on the thread of
 * the caller, like every use case.
 */
public fun KrosScope.mapDiagram(map: WorkspaceMap, node: String, depth: Int?, links: Int): MapDiagram =
    MapDiagrams(map).answer(this, node, depth, links)

internal class MapDiagrams(private val map: WorkspaceMap) {
    fun answer(scope: KrosScope, node: String, depth: Int?, links: Int): MapDiagram {
        if (map.symbolOf(SymbolId(node)) == null) return MapDiagram(error = NO_SUCH_SYMBOL)
        scope.memory.load()
        val request = DiagramRequest(DiagramKind.SYMBOL, node, depth ?: UNBOUNDED, links)
        return when (val result = DiagramBuilder(scope.graph()).build(request)) {
            is DiagramResult.Built -> MapDiagram(result.diagram, filesOf(result.diagram))
            is DiagramResult.NotSupported -> MapDiagram(error = notDrawn(result.symbol))
            is DiagramResult.BudgetTooSmall -> MapDiagram(error = tooSmall(result.minimumCharacters))
            is DiagramResult.NotFound -> MapDiagram(error = NO_SUCH_SYMBOL)
            is DiagramResult.Ambiguous -> MapDiagram(error = "More than one symbol answers to this name.")
        }
    }

    private fun filesOf(diagram: Diagram): Map<String, String> =
        diagram.nodes.mapNotNull { it.symbolId }.mapNotNull { id -> fileNode(id)?.let { id to it } }.toMap()

    private fun fileNode(symbolId: String): String? {
        val artifact = map.symbolOf(SymbolId(symbolId))?.declaration?.artifact ?: return null
        val place = map.placeOf(NodeRef.OfArtifact(artifact))
        return if (place == NONE) null else map.overview.nodes[place].id
    }

    private fun notDrawn(symbol: Symbol): String =
        "A diagram draws types and functions, and this is a ${symbol.kind.id}."

    private fun tooSmall(minimum: Int): String = "A diagram needs at least $minimum characters."

    private companion object {
        const val UNBOUNDED = 0
        const val NO_SUCH_SYMBOL = "The map has no such symbol."
    }
}
