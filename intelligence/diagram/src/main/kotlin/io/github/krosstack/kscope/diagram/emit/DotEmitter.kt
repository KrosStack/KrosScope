package io.github.krosstack.kscope.diagram.emit

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DrawnState

public object DotEmitter {
    public fun render(diagram: Diagram): String = buildString {
        appendLine("digraph diagram {")
        appendLine("  graph [label=\"${diagram.title.dotEscape()}\", labelloc=t];")
        appendLine("  rankdir=LR;")
        diagram.nodes.forEach { node -> appendLine("  ${node.id} [label=\"${node.label.dotEscape()}\"];") }
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
        diagram.edges.forEach { edge ->
            val style = when (edge.weakest) {
                DrawnState.EXACT -> "solid"
                DrawnState.PROBABLE, DrawnState.HEURISTIC -> "dashed"
                DrawnState.UNRESOLVED, DrawnState.NOT_ANALYSED -> "dotted"
            }
            val label = ((if (edge.weakest >= DrawnState.UNRESOLVED) "? " else "") + edge.label).dotEscape()
            appendLine("  ${edge.from} -> ${edge.to} [label=\"$label\", style=$style];")
        }
        appendLine("  legend [label=\"${diagram.legend.joinToString("; ").dotEscape()}\", shape=note];")
        val omitted = diagram.omitted.linksByState.entries.joinToString(
            ", ",
        ) { "${it.key.name.lowercase().replace('_', '-')}=${it.value}" }
        appendLine("  footer [label=\"Omitted links: $omitted\", shape=note];")
        appendLine("}")
    }
}
