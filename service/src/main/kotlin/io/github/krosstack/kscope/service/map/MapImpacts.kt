package io.github.krosstack.kscope.service.map

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.retrieval.ImpactEntry
import io.github.krosstack.kscope.retrieval.ImpactResult
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.map.tree.NONE

private sealed interface ImpactQuery {
    data class Text(val value: String) : ImpactQuery

    data class Refused(val reason: String) : ImpactQuery
}

/** Asks the impact question for a node or a symbol of the map and answers in positions of its tree. */
internal class MapImpacts(private val map: WorkspaceMap) {
    fun answer(scope: KrosScope, nodeId: String?, symbolId: String?): MapImpact {
        val requested = symbolId ?: nodeId.orEmpty()
        return when (val query = query(nodeId, symbolId)) {
            is ImpactQuery.Refused -> MapImpact(requested, error = query.reason)
            is ImpactQuery.Text -> ask(scope, requested, query.value)
        }
    }

    private fun ask(scope: KrosScope, requested: String, text: String): MapImpact {
        val result = scope.impact(text)
        if (result.roots.isEmpty()) return MapImpact(requested, error = "Nothing in the index answers to $text.")
        return mapped(requested, result)
    }

    private fun query(nodeId: String?, symbolId: String?): ImpactQuery = when {
        symbolId != null -> symbolQuery(symbolId)
        nodeId != null -> nodeQuery(nodeId)
        else -> ImpactQuery.Refused("Name a node or a symbol.")
    }

    private fun symbolQuery(symbolId: String): ImpactQuery = when (map.symbolOf(SymbolId(symbolId))) {
        null -> ImpactQuery.Refused(NO_SUCH_SYMBOL)
        else -> ImpactQuery.Text(symbolId)
    }

    private fun nodeQuery(nodeId: String): ImpactQuery {
        val node = map.positionOf(nodeId)?.let { map.overview.nodes[it] } ?: return ImpactQuery.Refused(NO_SUCH_NODE)
        val text = when (node.type) {
            "file" -> node.path
            "module" -> nodeId.removePrefix("m:").takeUnless { it.endsWith("//(root files)") }
            else -> null
        }
        return text?.let { ImpactQuery.Text(it) } ?: ImpactQuery.Refused(ONLY_FILES_MODULES_SYMBOLS)
    }

    private fun mapped(requested: String, result: ImpactResult): MapImpact {
        val entries = result.affected.mapNotNull(::entry)
        return MapImpact(
            requested = requested,
            roots = result.roots.map(map::placeOf).filter { it != NONE }.distinct().sorted(),
            affected = entries.take(LIMIT),
            tests = result.tests.map { map.placeOf(NodeRef.OfArtifact(it)) }.filter { it != NONE },
            unresolved = result.unresolved.size,
            truncated = entries.size > LIMIT,
        )
    }

    private fun entry(entry: ImpactEntry): MapImpactEntry? {
        val node = map.placeOf(entry.node)
        if (node == NONE) return null
        val symbol = (entry.node as? NodeRef.OfSymbol)?.let { map.symbolOf(it.id) }
        return MapImpactEntry(node, symbol?.name, symbol?.id?.value, entry.distance, entry.certainty)
    }

    private companion object {
        const val LIMIT = 5_000
        const val NO_SUCH_SYMBOL = "The map has no such symbol."
        const val NO_SUCH_NODE = "The map has no such node."
        const val ONLY_FILES_MODULES_SYMBOLS = "Impact is answered for files, modules and symbols."
    }
}
