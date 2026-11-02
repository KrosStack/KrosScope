package io.github.krosstack.kscope.service.map

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.RankedModule
import io.github.krosstack.kscope.retrieval.RankedSymbol
import io.github.krosstack.kscope.service.map.tree.NONE

/** Shows what the ranking of a task points at in positions of the tree; what the tree has no node for is left out. */
internal class MapTasks(private val map: WorkspaceMap) {
    fun of(result: AnalysisResult) = MapTask(
        query = result.query.text,
        terms = result.query.terms,
        confidence = result.confidence,
        files = result.files.mapNotNull(::file),
        tests = result.tests.mapNotNull(::file),
        symbols = result.symbols.mapNotNull(::symbol),
        modules = result.modules.mapNotNull(::module),
    )

    private fun file(ranked: RankedFile): MapRankedFile? {
        val node = map.placeOf(NodeRef.OfArtifact(ranked.artifact))
        val reasons = ranked.reasons.map { it.signal.wireName() }.distinct()
        return if (node == NONE) null else MapRankedFile(node, ranked.score, ranked.role.wireName(), reasons)
    }

    private fun symbol(ranked: RankedSymbol): MapRankedSymbol? {
        val node = map.placeOf(NodeRef.OfSymbol(ranked.symbol.id))
        val symbol = ranked.symbol
        return if (node == NONE) null else MapRankedSymbol(node, symbol.id.value, symbol.name, ranked.score)
    }

    private fun module(ranked: RankedModule): MapRankedModule? {
        val node = map.placeOf(NodeRef.OfModule(ranked.id))
        return if (node == NONE) null else MapRankedModule(node, ranked.score)
    }
}
