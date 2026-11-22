package io.github.krosstack.kscope.diagram.emit

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramEdge
import io.github.krosstack.kscope.diagram.DrawnState

public object DotEmitter {
    public fun render(diagram: Diagram): String = buildString {
        appendLine("digraph diagram {")
        appendLine("  graph [label=\"${diagram.title.dotEscape()}\", labelloc=t];")
        appendLine("  rankdir=LR;")
        diagram.nodes.forEach { node ->
            val marked = if (node.selected) ", style=filled, fillcolor=gold, penwidth=2" else ""
            appendLine("  ${node.id} [label=\"${node.label.dotEscape()}\"$marked];")
        }
        diagram.groups.forEach { group ->
            appendLine("  subgraph cluster_${group.id} {")
            appendLine("    label=\"${group.label.dotEscape()}\";")
            group.members.forEach { appendLine("    $it;") }
            appendLine("  }")
        }
        diagram.reasons.forEach { reason ->
            val label = "${reason.reason} (${reason.count})".middleCut().dotEscape()
            appendLine("  ${reason.id} [label=\"$label\", shape=box, style=dotted];")
        }
        diagram.edges.forEach { appendEdge(it) }
        appendLine("  legend [label=\"${diagram.legend.joinToString("; ").dotEscape()}\", shape=note];")
        appendLine("  footer [label=\"${diagram.omissionText()}\", shape=note];")
        appendLine("}")
    }

    private fun StringBuilder.appendEdge(edge: DiagramEdge) {
        val style = when (edge.weakest) {
            DrawnState.EXACT -> "solid"
            DrawnState.PROBABLE, DrawnState.HEURISTIC -> "dashed"
            DrawnState.UNRESOLVED, DrawnState.NOT_ANALYSED -> "dotted"
        }
        val label = edge.drawnLabel().dotEscape()
        val thick = if (edge.inherits()) ", penwidth=3" else ""
        appendLine("  ${edge.from} -> ${edge.to} [label=\"$label\", style=$style$thick];")
    }
}
