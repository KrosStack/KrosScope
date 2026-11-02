package io.github.krosstack.kscope.retrieval.analysis

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.retrieval.ImpactEntry
import io.github.krosstack.kscope.retrieval.ImpactResult
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.nodeKey
import io.github.krosstack.kscope.retrieval.target

internal class ImpactAnalysis(private val graph: RetrievalGraph) {
    fun analyze(text: String): ImpactResult {
        val roots = graph.resolve(text)
        val artifactRoots = roots.filterIsInstance<NodeRef.OfArtifact>().map { it.ref }.toSet()
        val expandedRoots = (roots + graph.nodes(artifactRoots)).distinct().sortedBy(::nodeKey)
        val reached = expandedRoots.associateWith { ImpactEntry(it, 0, "exact", emptyList()) }.toMutableMap()
        val queue = ArrayDeque(expandedRoots)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val current = reached.getValue(node)
            steps(node).forEach { (next, edge) ->
                if (next !in reached) {
                    reached[next] = entry(next, current, edge)
                    queue.addLast(next)
                }
            }
        }
        val affected = reached.values.filter { it.distance > 0 }
            .sortedWith(compareBy<ImpactEntry> { it.distance }.thenBy { nodeKey(it.node) })
        val files = reached.keys.mapNotNull(graph::file).toSet()
        val facts = AnalysisFacts(graph)
        return ImpactResult(
            text,
            roots,
            affected,
            facts.scope(files),
            files.filter { it in graph.tests }.sortedBy(graph.locations::pathOf),
            facts.near(files).filter { it.target() == null },
        )
    }

    private fun steps(node: NodeRef): List<Pair<NodeRef, Relationship>> {
        val reverse = graph.incoming[node].orEmpty().filter { it.kind in REVERSE }.map { it.from to it }
        val forward = graph.outgoing[node].orEmpty().filter { it.kind in FORWARD }.mapNotNull { edge ->
            edge.target()?.let { it to edge }
        }
        return (reverse + forward).sortedBy { nodeKey(it.first) }
    }

    private fun entry(node: NodeRef, parent: ImpactEntry, edge: Relationship): ImpactEntry {
        val certainty = (edge.resolution as Resolution.Resolved).certainty
        val weakest = listOf(Certainty.valueOf(parent.certainty.uppercase()), certainty).maxBy { it.ordinal }
        return ImpactEntry(node, parent.distance + 1, weakest.name.lowercase(), parent.path + edge)
    }

    private companion object {
        val REVERSE = setOf(
            RelationKind.REFERENCES, RelationKind.CALLS, RelationKind.EXTENDS, RelationKind.IMPLEMENTS,
            RelationKind.OVERRIDES, RelationKind.TYPE_USES, RelationKind.IMPORTS, RelationKind.ACTUALIZES,
            RelationKind.TESTS, RelationKind.BINDS, RelationKind.DEPENDS_ON,
        )
        val FORWARD = setOf(RelationKind.GENERATES, RelationKind.BINDS)
    }
}
