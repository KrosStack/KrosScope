package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.retrieval.ranking.GraphRanking
import io.github.krosstack.kscope.spi.graph.GraphReader

public class GraphQueries(reader: GraphReader) {
    private val graph = RetrievalGraph(reader)

    public fun related(text: String): RelatedResult {
        val roots = resolve(text)
        val scores = GraphRanking(graph).rank(seeds(roots))
        return RelatedGraph(graph).result(text, roots, scores)
    }

    public fun graph(
        scope: GraphScope = GraphScope.MODULES,
        node: String? = null,
        depth: Int = 1,
        limit: Int = DEFAULT_LIMIT,
    ): GraphResult {
        require(depth in 0..MAX_DEPTH) { "Graph depth must be between 0 and $MAX_DEPTH." }
        require(limit in 1..MAX_LIMIT) { "Graph limit must be between 1 and $MAX_LIMIT." }
        return PersistedGraphView(graph).result(scope, node?.let(::resolve).orEmpty(), depth, limit)
    }

    private fun resolve(text: String): List<NodeRef> {
        val sourceSets = graph.structure.sourceSets.filter { it.id.value == text }
            .map { NodeRef.OfSourceSet(it.id) }
        val externals = graph.edges.flatMap { listOfNotNull(it.from, it.target()) }
            .filterIsInstance<NodeRef.OfExternal>().filter { it.coordinate == text }
        return (graph.resolve(text) + sourceSets + externals).distinct().sortedBy(::nodeKey)
    }

    private fun seeds(roots: List<NodeRef>): Map<NodeRef, Double> = roots.flatMap { root ->
        when (root) {
            is NodeRef.OfModule -> graph.artifacts.keys.filter { graph.module(it) == root.id }
                .map { NodeRef.OfArtifact(it) }

            is NodeRef.OfSourceSet -> graph.artifacts.values.filter { it.sourceSet == root.id }
                .map { NodeRef.OfArtifact(it.ref) }

            is NodeRef.OfArtifact, is NodeRef.OfSymbol, is NodeRef.OfExternal -> listOf(root)
        }
    }.distinct().sortedBy(::nodeKey).associateWith { 1.0 }

    public companion object {
        public const val DEFAULT_LIMIT: Int = 2_000
        public const val MAX_LIMIT: Int = 100_000
        public const val MAX_DEPTH: Int = 3
    }
}
