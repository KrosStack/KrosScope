package io.github.krosstack.kscope.diagram.build

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramEdge
import io.github.krosstack.kscope.diagram.DiagramNode
import io.github.krosstack.kscope.diagram.DiagramRequest
import io.github.krosstack.kscope.diagram.DiagramResult
import io.github.krosstack.kscope.diagram.DrawnState
import io.github.krosstack.kscope.diagram.Omission

public class DiagramCut {
    public fun apply(diagram: Diagram, request: DiagramRequest, measure: (Diagram) -> Int): DiagramResult {
        val collapsed = collapse(diagram)
        val floor = retain(collapsed, emptyList(), false)
        val minimum = measure(floor)
        if (request.characters < minimum) return DiagramResult.BudgetTooSmall(minimum)
        var edges = collapsed.edges.take(request.links)
        var result = retain(collapsed, edges, edges.size == collapsed.edges.size)
        if (measure(result) > request.characters) {
            result = retain(collapsed, edges, false)
            while (measure(result) > request.characters && edges.isNotEmpty()) {
                edges = edges.dropLast(1)
                result = retain(collapsed, edges, false)
            }
        }
        return DiagramResult.Built(result)
    }

    private fun collapse(diagram: Diagram): Diagram {
        val order = (diagram.nodes.map { it.id } + diagram.reasons.map { it.id }).withIndex()
            .associate { it.value to it.index }
        val edges = diagram.edges.groupBy { Triple(it.from, it.label, it.to) }.values.map { parallels ->
            val first = parallels.first()
            val states = DrawnState.entries.associateWith { state -> parallels.sumOf { it.byState[state] ?: 0 } }
                .filterValues { it > 0 }
            val evidence = parallels.flatMap { it.byEvidence.keys }.distinct().sorted().associateWith { key ->
                parallels.sumOf { it.byEvidence[key] ?: 0 }
            }
            val counts = evidence.entries.joinToString(",") { "${it.key.lowercase().replace('_', '-')}=${it.value}" }
            val stateCounts = states.entries.joinToString(",") {
                "${it.key.name.lowercase().replace('_', '-')}=${it.value}"
            }
            first.copy(
                label = "${first.label}; $counts; $stateCounts",
                weakest = states.keys.max(),
                byState = states,
                byEvidence = evidence,
            )
        }.sortedWith(
            compareBy(
                { order[it.from] },
                { it.label.substringBefore(';').substringBefore(" [cycle]") },
                { order[it.to] },
                { it.label },
            ),
        )
        return diagram.copy(edges = edges)
    }

    private fun retain(diagram: Diagram, edges: List<DiagramEdge>, allNodes: Boolean): Diagram {
        val used = edges.flatMap { listOf(it.from, it.to) }.toSet()
        val totals = DrawnState.entries.associateWith { state -> diagram.edges.sumOf { it.byState[state] ?: 0 } }
        val omitted = totals.mapValues { (state, total) -> total - edges.sumOf { it.byState[state] ?: 0 } }
        val summaries = totals.filter { (state, count) -> count > 0 && edges.none { state in it.byState } }
            .map { (state, count) ->
                DiagramNode(
                    "s${state.ordinal}",
                    "${state.name.lowercase()}: $count links",
                    "aggregate",
                    null,
                )
            }
        val nodes = diagram.nodes.filter { allNodes || it.id in used }
        val reasons = diagram.reasons.filter { it.id in used }.map { reason ->
            reason.copy(count = edges.filter { it.to == reason.id }.sumOf { it.byState.values.sum() })
        }
        val nodeCount = nodes.size + reasons.size
        val totalNodes = diagram.nodes.size + diagram.reasons.size
        val groups = diagram.groups.map { group ->
            group.copy(
                members = group.members.filter { id -> nodes.any { it.id == id } },
            )
        }
            .filter { it.members.isNotEmpty() }
        return diagram.copy(
            title = "${diagram.title}: $nodeCount of $totalNodes nodes, ${edges.size} of ${totals.values.sum()} links",
            nodes = nodes + summaries,
            groups = groups,
            edges = edges,
            reasons = reasons,
            omitted = Omission(totalNodes - nodeCount, omitted),
        )
    }
}
