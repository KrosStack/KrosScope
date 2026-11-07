package io.github.krosstack.kscope.diagram.build

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramEdge
import io.github.krosstack.kscope.diagram.DiagramRequest
import io.github.krosstack.kscope.spi.graph.GraphReader

public class ModulesDiagramBuilder {
    public fun build(reader: GraphReader, request: DiagramRequest): Diagram {
        val diagram = DiagramQuery(reader, request.kind).build(request)
        val components = stronglyConnectedComponents(diagram.edges)
        return diagram.copy(
            edges = diagram.edges.map { edge ->
                if (components.getValue(edge.from) == components.getValue(edge.to)) {
                    edge.copy(label = "${edge.label} [cycle]")
                } else {
                    edge
                }
            },
        )
    }

    private fun stronglyConnectedComponents(edges: List<DiagramEdge>): Map<String, Int> {
        val adjacency = edges.groupBy({ it.from }, { it.to })
        val reversed = edges.groupBy({ it.to }, { it.from })
        val visited = mutableSetOf<String>()
        val finished = mutableListOf<String>()
        adjacency.keys.forEach { appendFinishingOrder(it, adjacency, visited, finished) }
        val components = mutableMapOf<String, Int>()
        for (root in finished.asReversed()) {
            if (root in components) continue
            val component = components.size
            val pending = ArrayDeque(listOf(root))
            while (pending.isNotEmpty()) {
                val node = pending.removeLast()
                if (node in components) continue
                components[node] = component
                pending.addAll(reversed[node].orEmpty())
            }
        }
        return components
    }

    private fun appendFinishingOrder(
        root: String,
        adjacency: Map<String, List<String>>,
        visited: MutableSet<String>,
        finished: MutableList<String>,
    ) {
        if (!visited.add(root)) return
        val pending = ArrayDeque(listOf(root to adjacency[root].orEmpty().iterator()))
        while (pending.isNotEmpty()) {
            val (node, neighbors) = pending.last()
            if (neighbors.hasNext()) {
                val next = neighbors.next()
                if (visited.add(next)) pending.addLast(next to adjacency[next].orEmpty().iterator())
            } else {
                pending.removeLast()
                finished.add(node)
            }
        }
    }
}
