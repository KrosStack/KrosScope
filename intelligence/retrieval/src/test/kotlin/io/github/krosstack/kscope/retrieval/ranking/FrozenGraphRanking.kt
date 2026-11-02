package io.github.krosstack.kscope.retrieval.ranking

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.node
import io.github.krosstack.kscope.retrieval.nodeKey

/** The ranking iteration as it was before it ran on arrays, kept to show that the scores did not move. */
internal class FrozenGraphRanking(graph: RetrievalGraph) {
    private val neighbors = SemanticAdjacency(graph)

    fun rank(seeds: Map<NodeRef, Double>): Map<NodeRef, Double> {
        if (seeds.isEmpty()) return emptyMap()
        val nodes = neighborhood(seeds.keys)
        val members = nodes.toHashSet()
        val adjacency = nodes.associateWith { neighbors.arcs(it).filter { arc -> arc.node in members } }
        val total = seeds.values.sum()
        val restart = nodes.associateWith { (seeds[it] ?: 0.0) / total }
        var scores = restart
        repeat(ITERATIONS) { scores = iterate(scores, restart, adjacency) }
        return scores
    }

    private fun neighborhood(seeds: Set<NodeRef>): List<NodeRef> {
        val seen = seeds.toMutableSet()
        var frontier = seeds
        repeat(HOPS) { _ ->
            frontier = frontier.flatMap { neighbors.arcs(it).map(RankingArc::node) }.filter { it !in seen }.toSet()
            seen.addAll(frontier)
        }
        return seen.sortedBy(::nodeKey)
    }

    private fun iterate(
        scores: Map<NodeRef, Double>,
        restart: Map<NodeRef, Double>,
        adjacency: Map<NodeRef, List<RankingArc>>,
    ): Map<NodeRef, Double> {
        val next = restart.mapValues { it.value * RESTART }.toMutableMap()
        var dangling = 0.0
        scores.forEach { (node, score) ->
            val arcs = adjacency[node].orEmpty()
            val total = arcs.sumOf { it.weight }
            if (total == 0.0) {
                dangling += score * (1 - RESTART)
            } else {
                arcs.forEach { next.merge(it.node, score * (1 - RESTART) * it.weight / total, Double::plus) }
            }
        }
        restart.forEach { (node, value) -> next.merge(node, dangling * value, Double::plus) }
        return next
    }

    private companion object {
        const val HOPS = 3
        const val ITERATIONS = 20
        const val RESTART = 0.35
    }
}
