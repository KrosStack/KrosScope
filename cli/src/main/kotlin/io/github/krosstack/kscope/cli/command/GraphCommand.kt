package io.github.krosstack.kscope.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import io.github.krosstack.kscope.cli.CommandGroup
import io.github.krosstack.kscope.cli.NodeAnswer
import io.github.krosstack.kscope.cli.OutputFormat
import io.github.krosstack.kscope.cli.ResolvedOptions
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.answerAbout
import io.github.krosstack.kscope.cli.forward
import io.github.krosstack.kscope.cli.guarded
import io.github.krosstack.kscope.cli.help.FormatGuide
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.reportAmbiguity
import io.github.krosstack.kscope.cli.requireIndex
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.text.GraphDot
import io.github.krosstack.kscope.cli.text.GraphText
import io.github.krosstack.kscope.cli.textContext
import io.github.krosstack.kscope.cli.useService
import io.github.krosstack.kscope.cli.withProgress
import io.github.krosstack.kscope.model.WorkspaceLocations
import io.github.krosstack.kscope.retrieval.GraphQueries
import io.github.krosstack.kscope.retrieval.GraphResult
import io.github.krosstack.kscope.retrieval.GraphScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ProgressListener
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.indexLevel
import io.github.krosstack.kscope.service.workspaceLocations

internal class GraphCommand : CliktCommand(name = "graph") {
    private val common by RootOptions(SharedOption.QUERY, FormatGuide.GRAPH)
    private val scope by option(
        "--scope",
        help = "What to show: modules (dependencies between modules), workspace (everything in the index) or " +
            "neighborhood (what surrounds the --node target).",
    ).choice(
        "modules" to GraphScope.MODULES,
        "workspace" to GraphScope.WORKSPACE,
        "neighborhood" to GraphScope.NEIGHBORHOOD,
    ).default(GraphScope.MODULES, defaultForHelp = "modules")
    private val node by option(
        "--node",
        metavar = "TARGET",
        help = "The symbol, module or file to centre a neighborhood on: a symbol name or id, a module path or a " +
            "file path.",
    )
    private val depth by option(
        "--depth",
        help = "How many relationships away a neighborhood reaches, from 0 to ${GraphQueries.MAX_DEPTH}.",
    ).int().restrictTo(0..GraphQueries.MAX_DEPTH).default(1)
    private val limit by option(
        "--limit",
        help = "The most modules, files, symbols and relationships to return; the result says when it was cut.",
    ).int().restrictTo(1..GraphQueries.MAX_LIMIT).default(GraphQueries.DEFAULT_LIMIT)

    override val helpTags = CommandGroup.tag(CommandGroup.INSPECT)

    override fun help(context: Context) = "Shows how the workspace's modules and files depend on each other.\n\n" +
        "Text lists each source with what it points at. Use --format dot to draw the graph with Graphviz, or " +
        "--format json for the evidence of every relationship. It shows the dependencies between modules unless " +
        "--scope asks for more. Run `kscope scan` first."

    override fun helpEpilog(context: Context) = listOf(
        "Examples:",
        "  kscope graph",
        "  kscope graph --scope neighborhood --node path/to/Task.kt --depth 2",
        "  kscope graph --format dot > modules.dot",
    ).joinToString("\n")

    override fun run() {
        val options = resolve(common)
        checkOptions()
        forward(options)
        val answer = guarded { withProgress(options) { listener -> query(options, listener) } }
        when (answer) {
            is NodeAnswer.Ambiguous -> reportAmbiguity(answer, options.format)
            is NodeAnswer.Found -> echo(render(answer.value, answer.level, options, answer.locations))
        }
    }

    private fun checkOptions() {
        if (scope == GraphScope.NEIGHBORHOOD && node.isNullOrBlank()) {
            throw UsageError("--scope neighborhood requires --node.")
        }
        if (scope != GraphScope.NEIGHBORHOOD && node != null) {
            throw UsageError("--node requires --scope neighborhood.")
        }
    }

    private fun query(options: ResolvedOptions, listener: ProgressListener): NodeAnswer<GraphResult> =
        useService(options, OpenOptions(progress = listener)) { service ->
            val centre = node
            if (centre == null) {
                service.requireIndex()
                NodeAnswer.Found(
                    service.graphView(scope, null, depth, limit),
                    service.workspaceLocations(),
                    service.indexLevel(),
                )
            } else {
                service.answerAbout(centre) { id -> service.graphView(scope, id, depth, limit) }
            }
        }

    private fun render(
        result: GraphResult,
        level: ScanLevel?,
        options: ResolvedOptions,
        locations: WorkspaceLocations,
    ) = when (options.format) {
        OutputFormat.JSON -> ServiceJson.encode(result, level)
        OutputFormat.DOT, OutputFormat.MARKDOWN -> GraphDot.render(result)
        OutputFormat.TEXT -> GraphText.render(result, textContext(options, locations))
    }
}
