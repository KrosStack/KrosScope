package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef

internal class RelatedGraph(private val graph: RetrievalGraph) {
    fun result(requested: String, roots: List<NodeRef>, scores: Map<NodeRef, Double>): RelatedResult {
        val positive = scores.filterValues { it > 0.0 }
        val files = files(positive)
        val symbols = symbols(positive)
        val edges = graph.edges.distinct().filter { edge ->
            edge.from in positive && (edge.target() == null || edge.target() in positive)
        }.sortedBy(::graphEdgeKey)
        return RelatedResult(
            requested,
            roots,
            files.take(RESULT_LIMIT),
            symbols.take(RESULT_LIMIT),
            edges.take(EDGE_LIMIT),
            files.size > RESULT_LIMIT || symbols.size > RESULT_LIMIT || edges.size > EDGE_LIMIT,
        )
    }

    private fun files(scores: Map<NodeRef, Double>): List<RankedFile> = scores.entries
        .mapNotNull { (node, score) -> graph.file(node)?.let { it to score } }
        .groupBy({ it.first }, { it.second })
        .filter { (file, _) -> file in graph.artifacts && graph.artifacts[file]?.generated == null }
        .map { (file, values) -> rankedFile(file, values.sum()) }
        .sortedWith(compareByDescending<RankedFile> { it.score }.thenBy { nodeKey(NodeRef.OfArtifact(it.artifact)) })

    private fun rankedFile(file: ArtifactRef, score: Double): RankedFile = RankedFile(
        file,
        score,
        FileRole.READ,
        listOf(SignalContribution(SignalKind.GRAPH, score)),
    )

    private fun symbols(scores: Map<NodeRef, Double>): List<RankedSymbol> = scores.entries.mapNotNull { (node, score) ->
        val symbol = (node as? NodeRef.OfSymbol)?.let { graph.symbols[it.id] }
        symbol?.let { RankedSymbol(it, score, listOf(SignalContribution(SignalKind.GRAPH, score))) }
    }.sortedWith(compareByDescending<RankedSymbol> { it.score }.thenBy { it.symbol.id.value })

    private companion object {
        const val RESULT_LIMIT = 50
        const val EDGE_LIMIT = 2_000
    }
}
