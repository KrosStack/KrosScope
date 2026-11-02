package io.github.krosstack.kscope.retrieval.ranking

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.retrieval.nodeKey
import io.github.krosstack.kscope.spi.search.SearchHit

internal object LexicalSeeds {
    fun weights(symbols: List<SearchHit>, artifacts: List<SearchHit>): Map<NodeRef, Double> =
        ranking(symbols, RankingWeights.SYMBOL) + ranking(artifacts, RankingWeights.ARTIFACT)

    private fun ranking(hits: List<SearchHit>, budget: Double): Map<NodeRef, Double> {
        val ranked = hits.filter { it.score > 0.0 && it.score.isFinite() }.distinctBy { it.node }
            .sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { nodeKey(it.node) })
        val weights = ranked.mapIndexed { index, hit -> hit.node to 1.0 / (RankingWeights.FUSION_OFFSET + index + 1) }
        val total = weights.sumOf { it.second }
        return weights.associate { (node, score) -> node to score * budget / total }
    }
}
