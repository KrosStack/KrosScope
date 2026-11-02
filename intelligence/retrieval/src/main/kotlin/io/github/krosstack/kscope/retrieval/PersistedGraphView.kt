package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship

internal class PersistedGraphView(private val graph: RetrievalGraph) {
    fun result(scope: GraphScope, roots: List<NodeRef>, depth: Int, limit: Int): GraphResult {
        val candidates = when (scope) {
            GraphScope.MODULES -> modules()
            GraphScope.WORKSPACE -> workspace()
            GraphScope.NEIGHBORHOOD -> neighborhood(roots, depth)
        }
        val nodes = candidates.take(limit).toSet()
        val candidateNodes = candidates.toSet()
        val allEdges = graph.edges.distinct().filter { included(it, candidateNodes) }.sortedBy(::graphEdgeKey)
        val edges = allEdges.filter { included(it, nodes) }
        return GraphResult(
            scope,
            nodes.sortedBy(::nodeKey).map(::describe),
            edges.take(limit),
            nodes.size < candidates.size || edges.size < allEdges.size || edges.size > limit,
        )
    }

    private fun modules(): List<NodeRef> {
        val modules = graph.structure.modules.map { NodeRef.OfModule(it.id) }.toSet()
        val externals = graph.edges.filter { it.from in modules }.mapNotNull { it.target() as? NodeRef.OfExternal }
        return (modules + externals).sortedBy(::nodeKey)
    }

    private fun workspace(): List<NodeRef> = buildSet {
        graph.structure.modules.forEach { add(NodeRef.OfModule(it.id)) }
        graph.structure.sourceSets.forEach { add(NodeRef.OfSourceSet(it.id)) }
        graph.artifacts.keys.forEach { add(NodeRef.OfArtifact(it)) }
        graph.symbols.keys.forEach { add(NodeRef.OfSymbol(it)) }
        graph.edges.forEach { edge ->
            add(edge.from)
            edge.target()?.let(::add)
        }
    }.sortedBy(::nodeKey)

    private fun neighborhood(roots: List<NodeRef>, depth: Int): List<NodeRef> {
        val seen = roots.toMutableSet()
        val ordered = roots.sortedBy(::nodeKey).toMutableList()
        var frontier = roots.toSet()
        repeat(depth) { _ ->
            frontier = frontier.flatMap { node ->
                graph.outgoing[node].orEmpty().mapNotNull { it.target() } +
                    graph.incoming[node].orEmpty().map { it.from }
            }.filter { it !in seen }.toSet()
            val next = frontier.sortedBy(::nodeKey)
            ordered.addAll(next)
            seen.addAll(next)
        }
        return ordered
    }

    private fun included(edge: Relationship, nodes: Set<NodeRef>): Boolean =
        edge.from in nodes && (edge.target() == null || edge.target() in nodes)

    private fun describe(node: NodeRef): GraphNode {
        val label = when (node) {
            is NodeRef.OfArtifact -> graph.locations.pathOf(node.ref)
            is NodeRef.OfSymbol -> symbolLabel(node)
            is NodeRef.OfModule -> graph.structure.modules.find { it.id == node.id }?.path ?: node.id.value
            is NodeRef.OfSourceSet -> graph.sourceSets[node.id]?.name ?: node.id.value
            is NodeRef.OfExternal -> node.coordinate
        }
        return GraphNode(node, label, graph.declaredFile(node))
    }

    private fun symbolLabel(node: NodeRef.OfSymbol): String {
        val symbol = graph.symbols[node.id]
        return symbol?.qualifiedName ?: symbol?.name ?: node.id.value
    }
}

internal fun graphEdgeKey(edge: Relationship): String = KscopeJson.encodeToString(edge)
