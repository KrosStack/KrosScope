package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.Column
import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.cli.text.format.NodeLabels
import io.github.krosstack.kscope.cli.text.format.Table
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.cli.text.format.TextDocument
import io.github.krosstack.kscope.cli.text.format.Tone
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.WorkspaceLocations
import io.github.krosstack.kscope.retrieval.GraphResult
import io.github.krosstack.kscope.retrieval.GraphScope

/** The result of `graph` for a person: who points at what, one line per source and kind of relationship. */
internal object GraphText {
    private const val SOURCES_SHOWN = 20
    private const val TARGETS_SHOWN = 6

    fun render(result: GraphResult, context: TextContext): String {
        val document = TextDocument(context)
        if (result.nodes.isEmpty()) {
            return document.conclusion("The graph for this scope is empty.", Tone.WARNING).toString()
        }
        document.conclusion(conclusion(result), Tone.GOOD)
        val lines = groups(result, context.locations)
        val table = Table(listOf(Column(), Column(), Column()))
        lines.take(SOURCES_SHOWN).forEach { table.row(it.source, it.kind, it.describeTargets()) }
        if (lines.isEmpty()) document.line("  No relationships within this scope.") else document.line().table(table)
        document.more(lines.size - SOURCES_SHOWN)
        if (result.truncated) document.hint("The graph was cut at its limit; raise it with --limit.")
        document.hint("Use --format dot for Graphviz, or --format json for evidence and unresolved references.")
        return document.toString()
    }

    private fun conclusion(result: GraphResult): String {
        val name = when (result.scope) {
            GraphScope.MODULES -> "Module graph"
            GraphScope.WORKSPACE -> "Workspace graph"
            GraphScope.NEIGHBORHOOD -> "Neighborhood graph"
        }
        val modules = result.nodes.count { it.ref is NodeRef.OfModule }
        val counts = if (result.scope == GraphScope.MODULES) "${Format.noun(modules, "module")}, " else ""
        return "$name: $counts${Format.noun(result.nodes.size, "node")}, " +
            "${Format.noun(result.relationships.size, "relationship")}."
    }

    private class Line(val source: String, val kind: String, val targets: List<String>, val libraries: Int) {
        fun describeTargets(): String {
            val shown = Format.listing(targets, TARGETS_SHOWN)
            val outside = if (libraries == 0) "" else Format.noun(libraries, "external library", "external libraries")
            return listOf(shown, outside).filter { it.isNotEmpty() }.joinToString("; ")
        }
    }

    private fun groups(result: GraphResult, locations: WorkspaceLocations): List<Line> = result.relationships
        .groupBy { it.from to it.kind }
        .map { (key, edges) ->
            val (libraries, inside) = edges.partition { isExternal(it) }
            Line(
                NodeLabels.of(key.first, locations),
                key.second.name.lowercase().replace('_', ' '),
                inside.map { target(it, locations) },
                libraries.size,
            )
        }

    private fun isExternal(edge: Relationship): Boolean =
        (edge.resolution as? Resolution.Resolved)?.target is NodeRef.OfExternal

    private fun target(edge: Relationship, locations: WorkspaceLocations): String {
        val label = NodeLabels.target(edge, locations)
        return if (edge.resolution is Resolution.Resolved) label else "$label (unresolved)"
    }
}
