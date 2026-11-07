package io.github.krosstack.kscope.diagram.emit

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramEdge
import io.github.krosstack.kscope.diagram.DrawnState

public object MermaidEmitter {
    public fun render(diagram: Diagram): String = buildString {
        appendLine("---")
        appendLine("title: \"${diagram.title.dotEscape()}\"")
        appendLine("config:")
        appendLine("  layout: dagre")
        appendLine("  theme: neutral")
        appendLine("---")
        appendLine("flowchart LR")
        diagram.groups.forEach { group ->
            appendLine("  subgraph ${group.id}[\"${group.label.mermaidEscape()}\"]")
            diagram.nodes.filter { it.id in group.members }.forEach { node ->
                appendLine("    ${node.id}[\"${node.label.mermaidEscape()}\"]")
            }
            appendLine("  end")
        }
        diagram.nodes.filter { it.group == null }.forEach { node ->
            val style = if (node.kind == "aggregate") ":::aggregate" else ""
            appendLine("  ${node.id}[\"${node.label.mermaidEscape()}\"]$style")
        }
        diagram.reasons.forEach { reason ->
            val label = "${reason.reason} (${reason.count})".middleCut().mermaidEscape()
            appendLine("  ${reason.id}[\"$label\"]:::reason")
        }
        diagram.edges.forEachIndexed { index, edge -> appendEdge(index, edge) }
        appendLine("  legend[\"${diagram.legend.joinToString("; ").mermaidEscape()}\"]:::metadata")
        val omitted = diagram.omitted.linksByState.entries.joinToString(
            ", ",
        ) { "${it.key.name.lowercase().replace('_', '-')}=${it.value}" }
        appendLine("  footer[\"Omitted links: $omitted\"]:::metadata")
        appendLine("  classDef reason stroke-dasharray:2 3")
        appendLine("  classDef aggregate stroke-dasharray:6 3")
        appendLine("  classDef metadata fill:#fff,stroke:#999")
    }

    private fun StringBuilder.appendEdge(index: Int, edge: DiagramEdge) {
        val unknown = edge.weakest >= DrawnState.UNRESOLVED
        val label = ((if (unknown) "? " else "") + edge.label).mermaidEscape()
        val arrow = if (edge.weakest == DrawnState.EXACT) "-->" else "-.->"
        appendLine("  ${edge.from} $arrow|\"$label\"| ${edge.to}")
        when (edge.weakest) {
            DrawnState.EXACT -> Unit
            DrawnState.PROBABLE, DrawnState.HEURISTIC -> appendLine("  linkStyle $index stroke-dasharray:6 3")
            DrawnState.UNRESOLVED, DrawnState.NOT_ANALYSED -> appendLine("  linkStyle $index stroke-dasharray:2 3")
        }
    }
}
