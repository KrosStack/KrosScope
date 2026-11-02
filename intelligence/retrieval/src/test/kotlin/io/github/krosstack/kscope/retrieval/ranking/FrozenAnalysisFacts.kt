package io.github.krosstack.kscope.retrieval.ranking

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.RiskFact
import io.github.krosstack.kscope.retrieval.target

/** The change facts as they were before they shared one pass over the relationships, kept as a reference. */
internal class FrozenAnalysisFacts(private val graph: RetrievalGraph) {
    fun nodes(files: Set<ArtifactRef>): Set<NodeRef> = buildSet {
        files.forEach { add(NodeRef.OfArtifact(it)) }
        graph.symbols.values.filter { it.declaration?.artifact in files }.forEach { add(NodeRef.OfSymbol(it.id)) }
    }

    fun near(files: Set<ArtifactRef>): List<Relationship> {
        val nodes = nodes(files)
        return graph.edges.filter { it.from in nodes || it.target() in nodes }
    }

    fun risks(files: Set<ArtifactRef>): List<RiskFact> = files.sortedBy { it.path.value }.flatMap { file ->
        val node = NodeRef.OfArtifact(file)
        val edges = near(setOf(file))
        listOf(
            RiskFact(node, "fan-in", edges.count { enters(it, file) }.toDouble()),
            RiskFact(node, "fan-out", edges.count { leaves(it, file) }.toDouble()),
            RiskFact(node, "size.lines", graph.artifacts[file]?.lineCount?.toDouble() ?: 0.0),
            RiskFact(node, "unresolved", edges.count { it.target() == null }.toDouble()),
            RiskFact(node, "test-relationships", edges.count { it.kind == RelationKind.TESTS }.toDouble()),
        ) + graph.reader.metrics(node).map { RiskFact(node, it.name, it.value) }
    }

    private fun enters(edge: Relationship, file: ArtifactRef): Boolean =
        graph.file(edge.from) != file && edge.target()?.let(graph::file) == file

    private fun leaves(edge: Relationship, file: ArtifactRef): Boolean =
        graph.file(edge.from) == file && edge.target()?.let { graph.file(it) != file } == true
}
