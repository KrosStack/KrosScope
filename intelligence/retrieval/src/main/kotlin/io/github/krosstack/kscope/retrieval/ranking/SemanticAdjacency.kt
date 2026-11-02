package io.github.krosstack.kscope.retrieval.ranking

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.nodeKey
import io.github.krosstack.kscope.retrieval.target

internal data class RankingArc(val node: NodeRef, val weight: Double)

internal class SemanticAdjacency(private val graph: RetrievalGraph) {
    private val memberships = memberships()

    fun arcs(node: NodeRef): List<RankingArc> {
        if (!rankedNode(node)) return emptyList()
        val forward = graph.outgoing[node].orEmpty().mapNotNull { edge ->
            edge.target()?.takeIf(::rankedNode)?.let { RankingArc(it, GraphRanking.weight(edge)) }
        }
        val reverse = graph.incoming[node].orEmpty().filter { rankedNode(it.from) }.map { edge ->
            RankingArc(edge.from, GraphRanking.weight(edge) * GraphRanking.REVERSE)
        }
        val persisted = (forward + reverse).filter { it.weight > 0.0 }
        val covered = persisted.map(RankingArc::node).toSet()
        return (persisted + memberships[node].orEmpty().filter { it.node !in covered })
            .sortedBy { nodeKey(it.node) }
    }

    private fun memberships(): Map<NodeRef, List<RankingArc>> {
        val result = mutableMapOf<NodeRef, MutableList<RankingArc>>()
        graph.symbols.values.sortedBy { it.id.value }.forEach { symbol ->
            val node = NodeRef.OfSymbol(symbol.id)
            symbol.declaration?.artifact?.takeIf { it in graph.artifacts }?.let { artifact ->
                addMembership(result, NodeRef.OfArtifact(artifact), node)
            }
            symbol.container?.takeIf { it in graph.symbols }?.let { container ->
                addMembership(result, NodeRef.OfSymbol(container), node)
            }
        }
        return result.mapValues { (_, arcs) -> arcs.distinct().sortedBy { nodeKey(it.node) } }
    }

    private fun addMembership(
        memberships: MutableMap<NodeRef, MutableList<RankingArc>>,
        parent: NodeRef,
        child: NodeRef,
    ) {
        if (parent == child) return
        memberships.getOrPut(parent, ::mutableListOf).add(RankingArc(child, GraphRanking.WEAK))
        memberships.getOrPut(child, ::mutableListOf).add(RankingArc(parent, GraphRanking.WEAK * GraphRanking.REVERSE))
    }

    private fun rankedNode(node: NodeRef): Boolean = node is NodeRef.OfArtifact || node is NodeRef.OfSymbol
}
