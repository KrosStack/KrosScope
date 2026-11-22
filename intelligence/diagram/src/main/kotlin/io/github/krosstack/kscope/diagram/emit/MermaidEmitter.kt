package io.github.krosstack.kscope.diagram.emit

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramEdge
import io.github.krosstack.kscope.diagram.DiagramNode
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
                appendLine("    ${node.id}[\"${node.label.mermaidEscape()}\"]${node.styleClass()}")
            }
            appendLine("  end")
        }
        diagram.nodes.filter { it.group == null }.forEach { node ->
            appendLine("  ${node.id}[\"${node.label.mermaidEscape()}\"]${node.styleClass()}")
        }
        diagram.reasons.forEach { reason ->
            val label = "${reason.reason} (${reason.count})".middleCut().mermaidEscape()
            appendLine("  ${reason.id}[\"$label\"]:::reason")
        }
        diagram.edges.forEachIndexed { index, edge -> appendEdge(index, edge) }
        appendLine("  legend[\"${diagram.legend.joinToString("; ").mermaidEscape()}\"]:::metadata")
        appendLine("  footer[\"${diagram.omissionText()}\"]:::metadata")
        appendLine("  classDef reason stroke-dasharray:2 3")
        appendLine("  classDef aggregate stroke-dasharray:6 3")
        appendLine("  classDef metadata fill:#fff,stroke:#999")
        if (diagram.nodes.any { it.selected }) {
            appendLine("  classDef selected fill:#ffe9a8,stroke:#000,stroke-width:3px")
        }
    }

    private fun DiagramNode.styleClass(): String = when {
        selected -> ":::selected"
        kind == "aggregate" -> ":::aggregate"
        else -> ""
    }

    private fun StringBuilder.appendEdge(index: Int, edge: DiagramEdge) {
        val label = edge.drawnLabel().mermaidEscape()
        val arrow = when {
            edge.inherits() -> "==>"
            edge.weakest == DrawnState.EXACT -> "-->"
            else -> "-.->"
        }
        appendLine("  ${edge.from} $arrow|\"$label\"| ${edge.to}")
        when (edge.weakest) {
            DrawnState.EXACT -> Unit
            DrawnState.PROBABLE, DrawnState.HEURISTIC -> appendLine("  linkStyle $index stroke-dasharray:6 3")
            DrawnState.UNRESOLVED, DrawnState.NOT_ANALYSED -> appendLine("  linkStyle $index stroke-dasharray:2 3")
        }
    }
}
