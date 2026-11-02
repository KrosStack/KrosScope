package io.github.krosstack.kscope.retrieval.ranking

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.nodeKey

internal class GraphRanking(graph: RetrievalGraph) {
    private val neighbors = SemanticAdjacency(graph)

    fun rank(seeds: Map<NodeRef, Double>): Map<NodeRef, Double> {
        if (seeds.isEmpty()) return emptyMap()
        val nodes = neighborhood(seeds.keys)
        val position = HashMap<NodeRef, Int>(nodes.size * 2)
        nodes.forEachIndexed { index, node -> position[node] = index }
        val total = seeds.values.sum()
        val restart = DoubleArray(nodes.size) { (seeds[nodes[it]] ?: 0.0) / total }
        val links = nodes.map { links(it, position) }
        var scores = restart
        repeat(ITERATIONS) { scores = iterate(scores, restart, links) }
        return LinkedHashMap<NodeRef, Double>(nodes.size * 2).also { ranked ->
            nodes.forEachIndexed { index, node -> ranked[node] = scores[index] }
        }
    }

    private fun neighborhood(seeds: Set<NodeRef>): List<NodeRef> {
        val seen = seeds.toMutableSet()
        var frontier = seeds
        repeat(HOPS) { _ ->
            frontier = frontier.flatMap { adjacent(it).map(RankingArc::node) }.filter { it !in seen }.toSet()
            seen.addAll(frontier)
        }
        return seen.sortedBy(::nodeKey)
    }

    private fun links(node: NodeRef, position: Map<NodeRef, Int>): Links {
        val arcs = adjacent(node).filter { it.node in position }
        return Links(
            IntArray(arcs.size) { position.getValue(arcs[it].node) },
            DoubleArray(arcs.size) { arcs[it].weight },
            arcs.sumOf { it.weight },
        )
    }

    private fun iterate(scores: DoubleArray, restart: DoubleArray, links: List<Links>): DoubleArray {
        val next = DoubleArray(restart.size) { restart[it] * RESTART }
        var dangling = 0.0
        scores.forEachIndexed { node, score ->
            val arcs = links[node]
            if (arcs.total == 0.0) {
                dangling += score * (1 - RESTART)
            } else {
                arcs.targets.forEachIndexed { arc, target ->
                    next[target] += score * (1 - RESTART) * arcs.weights[arc] / arcs.total
                }
            }
        }
        restart.forEachIndexed { node, value -> next[node] += dangling * value }
        return next
    }

    private class Links(val targets: IntArray, val weights: DoubleArray, val total: Double)

    private fun adjacent(node: NodeRef): List<RankingArc> = neighbors.arcs(node)

    companion object {
        private const val HOPS = 3
        private const val ITERATIONS = 20
        private const val RESTART = 0.35
        const val REVERSE = 0.8
        private const val STRONG = 1.0
        private const val MEDIUM = 0.5
        const val WEAK = 0.15
        private const val PROBABLE = 0.7
        private const val HEURISTIC = 0.4

        fun weight(edge: Relationship): Double {
            val resolved = edge.resolution as? Resolution.Resolved ?: return 0.0
            val certainty = when (resolved.certainty) {
                Certainty.EXACT -> 1.0
                Certainty.PROBABLE -> PROBABLE
                Certainty.HEURISTIC -> HEURISTIC
            }
            return kindWeight(edge) * certainty
        }

        private fun kindWeight(edge: Relationship): Double = when (edge.kind) {
            RelationKind.ACTUALIZES, RelationKind.GENERATES, RelationKind.BINDS,
            RelationKind.OVERRIDES, RelationKind.IMPLEMENTS,
            -> STRONG

            RelationKind.CALLS, RelationKind.EXTENDS, RelationKind.TESTS -> MEDIUM

            RelationKind.CO_CHANGES_WITH -> MEDIUM * (edge.attributes["confidence"]?.toDoubleOrNull() ?: 0.0)

            else -> WEAK
        }
    }
}
