package io.github.krosstack.kscope.retrieval.analysis

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.nodeKey
import io.github.krosstack.kscope.retrieval.target

internal data class BoundaryPathResult(val paths: List<List<Relationship>>, val truncated: Boolean)

internal class BoundaryPaths(private val graph: RetrievalGraph) {
    private val edges = graph.edges.filter { it.kind in AnalysisFacts.BOUNDARIES && it.target() != null }
        .sortedBy { KscopeJson.encodeToString(it) }
    private val adjacency = edges.flatMap { edge ->
        listOfNotNull(
            edge.target()?.let { edge.from to Step(it, edge) },
            edge.target()?.let { it to Step(edge.from, edge) },
        )
    }.groupBy({ it.first }, { it.second })

    fun between(selected: Set<ArtifactRef>): BoundaryPathResult {
        val starts = graph.nodes(selected).sortedBy(::nodeKey)
        val queue = ArrayDeque(starts.map { Walk(it, it, setOf(it), emptyList()) })
        val paths = linkedSetOf<List<Relationship>>()
        var explored = 0
        while (queue.isNotEmpty() && paths.size < MAX_PATHS && explored < MAX_WALKS) {
            val walk = queue.removeFirst()
            explored++
            if (connectsSelected(walk, selected)) paths.add(walk.path)
            if (walk.path.size < MAX_HOPS) queue.addAll(extend(walk))
        }
        return BoundaryPathResult(paths.sortedBy { KscopeJson.encodeToString(it) }, queue.isNotEmpty())
    }

    private fun connectsSelected(walk: Walk, selected: Set<ArtifactRef>): Boolean {
        val start = graph.file(walk.start)
        val end = graph.file(walk.node)
        return start != null && end != null && end in selected && artifactKey(start) < artifactKey(end)
    }

    private fun extend(walk: Walk): List<Walk> = adjacency[walk.node].orEmpty()
        .filter { it.node !in walk.visited }.map { step ->
            Walk(walk.start, step.node, walk.visited + step.node, walk.path + step.edge)
        }

    private fun artifactKey(file: ArtifactRef): String = "${file.repository.value}:${file.path.value}"

    private data class Step(val node: NodeRef, val edge: Relationship)
    private data class Walk(
        val start: NodeRef,
        val node: NodeRef,
        val visited: Set<NodeRef>,
        val path: List<Relationship>,
    )

    private companion object {
        const val MAX_HOPS = 3
        const val MAX_PATHS = 100
        const val MAX_WALKS = 2000
    }
}
