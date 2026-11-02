package io.github.krosstack.kscope.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import io.github.krosstack.kscope.cli.text.format.Column
import io.github.krosstack.kscope.cli.text.format.NodeLabels
import io.github.krosstack.kscope.cli.text.format.Table
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.WorkspaceLocations
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.NodeResolution
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.indexLevel
import io.github.krosstack.kscope.service.resolveNode
import io.github.krosstack.kscope.service.workspaceLocations

/** What a command that names a node got back: the answer, or the several nodes that the name could mean. */
internal sealed interface NodeAnswer<out T> {
    class Found<T>(val value: T, val locations: WorkspaceLocations, val level: ScanLevel?) : NodeAnswer<T>

    class Ambiguous(val resolution: NodeResolution, val level: ScanLevel?) : NodeAnswer<Nothing>
}

/** Resolves [query] to one node and asks [ask] about it; several candidates are returned for the caller to report. */
internal fun <T> KrosScope.answerAbout(query: String, ask: (String) -> T): NodeAnswer<T> {
    requireIndex()
    val resolution = resolveNode(query)
    return if (resolution.candidates.size > 1) {
        NodeAnswer.Ambiguous(resolution, indexLevel())
    } else {
        NodeAnswer.Found(ask(resolution.candidates.single()), workspaceLocations(), indexLevel())
    }
}

/** Prints the candidates of an ambiguous name and ends the command with the usage exit code. */
internal fun CliktCommand.reportAmbiguity(ambiguous: NodeAnswer.Ambiguous, format: OutputFormat): Nothing {
    val resolution = ambiguous.resolution
    echo(
        if (format == OutputFormat.JSON) {
            ServiceJson.encode(resolution, ambiguous.level)
        } else {
            ambiguityText(resolution)
        },
    )
    throw ProgramResult(ExitCodes.USAGE)
}

private fun ambiguityText(resolution: NodeResolution): String {
    val table = Table(listOf(Column("name"), Column("pass this to choose it")))
    resolution.candidates.forEach { table.row(NodeLabels.symbol(SymbolId(it)), it) }
    return (
        listOf("\"${resolution.requested}\" is ambiguous: ${resolution.candidates.size} declarations match.") +
            table.render() +
            "Run the command again with one of these values, or with path:line to pick by location."
        ).joinToString("\n")
}
