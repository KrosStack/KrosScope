package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.retrieval.GraphResult

/** The result of `graph` as Graphviz DOT, for programs and for drawing. */
internal object GraphDot {
    fun render(result: GraphResult): String = buildString {
        appendLine("digraph kscope {")
        appendLine("  graph [label=\"scope=${result.scope.name.lowercase()} truncated=${result.truncated}\"];")
        val ids = result.nodes.mapIndexed { index, node -> node.ref to "n$index" }.toMap()
        result.nodes.forEach { node -> appendLine("  ${ids.getValue(node.ref)} [label=\"${escape(node.label)}\"];") }
        result.relationships.forEachIndexed { index, edge -> appendEdge(index, edge, ids) }
        appendLine("}")
    }

    private fun StringBuilder.appendEdge(index: Int, edge: Relationship, ids: Map<NodeRef, String>) {
        val source = ids[edge.from] ?: return
        val target = when (val resolution = edge.resolution) {
            is Resolution.Resolved -> ids[resolution.target] ?: return
            is Resolution.Unresolved -> appendUnknown(index, resolution.hint)
            is Resolution.NotAnalysed -> appendUnknown(index, resolution.hint)
        }
        val label = "${edge.kind.name.lowercase()}\n${edge.evidenceClass.name.lowercase()}:${state(edge.resolution)}"
        appendLine("  $source -> $target [label=\"${escape(label)}\"];")
    }

    private fun StringBuilder.appendUnknown(index: Int, name: String): String {
        val id = "u$index"
        appendLine("  $id [label=\"${escape(name)}\", shape=box, style=dashed];")
        return id
    }

    private fun state(resolution: Resolution): String = when (resolution) {
        is Resolution.Resolved -> resolution.certainty.name.lowercase()
        is Resolution.Unresolved -> "unresolved:${resolution.reason.value}"
        is Resolution.NotAnalysed -> "not-analysed:${resolution.reason.value}"
    }

    private fun escape(text: String): String = text.map { character ->
        when (character) {
            '\\' -> "\\\\"
            '"' -> "\\\""
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            else -> if (character.isISOControl()) " " else character.toString()
        }
    }.joinToString("")
}
