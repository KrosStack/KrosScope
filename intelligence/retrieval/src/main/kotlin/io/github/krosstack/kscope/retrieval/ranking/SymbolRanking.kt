package io.github.krosstack.kscope.retrieval.ranking

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.retrieval.RankedSymbol
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.RetrievalStage
import io.github.krosstack.kscope.retrieval.SignalContribution
import io.github.krosstack.kscope.retrieval.SignalKind
import io.github.krosstack.kscope.spi.search.SearchHit

internal class SymbolRanking(private val graph: RetrievalGraph) {
    fun rank(lexical: List<SearchHit>, propagated: Map<NodeRef, Double>, stage: RetrievalStage): List<RankedSymbol> {
        val project = stage >= RetrievalStage.PROJECTION
        val lexicalScores = scores(lexical.associate { it.node to it.score }, project)
        val graphScores = if (stage >= RetrievalStage.GRAPH) scores(propagated, project) else emptyMap()
        val signals = listOf(
            contributions(lexicalScores, SignalKind.LEXICAL_SYMBOL, RankingWeights.SYMBOL),
            contributions(graphScores, SignalKind.GRAPH, RankingWeights.GRAPH),
        )
        return (lexicalScores.keys + graphScores.keys).map { id ->
            val reasons = signals.mapNotNull { it[id] }
            RankedSymbol(graph.symbols.getValue(id), reasons.sumOf { it.score }, reasons)
        }.sortedWith(compareByDescending<RankedSymbol> { it.score }.thenBy { it.symbol.id.value })
    }

    private fun scores(values: Map<NodeRef, Double>, project: Boolean): Map<SymbolId, Double> = values.entries
        .filter { it.value > 0.0 && it.value.isFinite() }
        .flatMap { (node, score) ->
            val id = (node as? NodeRef.OfSymbol)?.id ?: return@flatMap emptyList()
            val sources = if (project) sourceDeclarations(id, emptySet()).ifEmpty { listOf(id) } else listOf(id)
            sources.filter { it in graph.symbols }.map { it to score }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, groupScores) ->
            groupScores.max() + RankingWeights.DAMPING * (groupScores.sum() - groupScores.max())
        }

    private fun sourceDeclarations(id: SymbolId, seen: Set<SymbolId>): List<SymbolId> = if (id in seen) {
        emptyList()
    } else {
        graph.symbols[id]?.let { canonicalSources(it, seen + id) }.orEmpty()
    }

    private fun canonicalSources(symbol: Symbol, seen: Set<SymbolId>): List<SymbolId> {
        val sources = sourceNodes(symbol)
        return if (sources.isEmpty()) {
            listOf(symbol.id)
        } else {
            sources.filterIsInstance<NodeRef.OfSymbol>()
                .flatMap { sourceDeclarations(it.id, seen) }.distinct().sortedBy { it.value }
        }
    }

    private fun sourceNodes(symbol: Symbol): List<NodeRef> {
        val origin = (symbol.origin as? SymbolOrigin.Generated)?.sourceOfTruth
        val generatedBy = graph.incoming[NodeRef.OfSymbol(symbol.id)].orEmpty()
            .filter { it.kind == RelationKind.GENERATES }.map { it.from }
        val declarations = generatedBy.filterIsInstance<NodeRef.OfSymbol>()
        return when {
            origin is NodeRef.OfSymbol -> listOf(origin)
            declarations.isNotEmpty() -> declarations
            else -> origin?.let(::listOf) ?: generatedBy
        }
    }

    private fun contributions(
        scores: Map<SymbolId, Double>,
        kind: SignalKind,
        weight: Double,
    ): Map<SymbolId, SignalContribution> = scores.entries
        .sortedWith(compareByDescending<Map.Entry<SymbolId, Double>> { it.value }.thenBy { it.key.value })
        .mapIndexed { index, entry ->
            entry.key to SignalContribution(kind, weight / (RankingWeights.FUSION_OFFSET + index + 1))
        }.toMap()
}
