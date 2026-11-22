package io.github.krosstack.kscope.cli.diagram

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
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
import io.github.krosstack.kscope.cli.lookup.askedFrom
import io.github.krosstack.kscope.cli.lookup.fromFolder
import io.github.krosstack.kscope.cli.lookup.parentIndex
import io.github.krosstack.kscope.cli.noteChoice
import io.github.krosstack.kscope.cli.output.echoResult
import io.github.krosstack.kscope.cli.reportAmbiguity
import io.github.krosstack.kscope.cli.requireIndex
import io.github.krosstack.kscope.cli.resolveReading
import io.github.krosstack.kscope.cli.useService
import io.github.krosstack.kscope.cli.withProgress
import io.github.krosstack.kscope.diagram.DiagramKind
import io.github.krosstack.kscope.diagram.DiagramRequest
import io.github.krosstack.kscope.diagram.DiagramResult
import io.github.krosstack.kscope.service.NodeResolution
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ProgressListener
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.diagram.diagram
import io.github.krosstack.kscope.service.indexLevel
import io.github.krosstack.kscope.service.workspaceLocations

internal class DiagramCommand : CliktCommand(name = "diagram") {
    private val common by RootOptions(SharedOption.QUERY, FormatGuide.DIAGRAM)
    private val kind by argument("KIND", help = "What to draw: modules, components or symbol.").choice(
        "modules" to DiagramKind.MODULES,
        "components" to DiagramKind.COMPONENTS,
        "symbol" to DiagramKind.SYMBOL,
    )
    private val node by argument(
        "NODE",
        help = "Where to start. For modules a module path such as :core:model or a module id; for components a " +
            "repository name or id; for symbol a name, an id or a file location such as path/File.kt:42, which " +
            "symbol requires. Without it, modules and components draw everything.",
    ).optional()
    private val depth by option(
        "--depth",
        help = "How many steps away from NODE the diagram reaches. For modules and components, 0 draws NODE " +
            "alone. For symbol, 0 means no depth limit: the walk goes to the boundary, within the links and " +
            "read budgets.",
    ).int().restrictTo(0..Int.MAX_VALUE).default(DEFAULTS.depth)
    private val links by option(
        "--links",
        help = "The most lines to draw; relationships of one kind between the same two nodes share a line. " +
            "The footer of the diagram counts what was left out.",
    ).int().restrictTo(0..Int.MAX_VALUE).default(DEFAULTS.links)
    private val characters by option(
        "--characters",
        help = "The most characters the Mermaid text of the diagram may have. It limits every format alike, so " +
            "that the formats show the same diagram.",
    ).int().restrictTo(1..Int.MAX_VALUE).default(DEFAULTS.characters)

    override val helpTags = CommandGroup.tag(CommandGroup.INSPECT)

    override fun help(context: Context) = "Draws the modules, the repositories or one symbol as a diagram.\n\n" +
        "modules shows how modules depend on each other, components how repositories connect, and symbol the " +
        "types around a class or the calls around a function. The diagram stays within --links and " +
        "--characters and says what it left out. Use --format dot for Graphviz, or --format json for programs. " +
        "Run `kscope scan` first."

    override fun helpEpilog(context: Context) = listOf(
        "Examples:",
        "  kscope diagram modules",
        "  kscope diagram components --format dot > components.dot",
        "  kscope diagram symbol KrosScope --depth 2 --format json",
    ).joinToString("\n")

    override fun run() {
        val options = resolveReading(common, defaultFormat = OutputFormat.MERMAID)
        val format = DiagramFormat.of(options.format)
        requireNodeForSymbol()
        forward(options)
        val answer = guarded { withProgress(options) { listener -> query(options, listener) } }
        noteChoice(answer, node.orEmpty(), options)
        when (answer) {
            is NodeAnswer.Ambiguous -> reportAmbiguity(answer, options)
            is NodeAnswer.Found -> show(answer, format, options)
        }
    }

    private fun requireNodeForSymbol() {
        if (kind == DiagramKind.SYMBOL && node.isNullOrBlank()) {
            throw UsageError(
                "diagram symbol needs a node: a symbol name, a symbol id or a file location such as path/File.kt:42.",
            )
        }
    }

    private fun query(options: ResolvedOptions, listener: ProgressListener): NodeAnswer<DiagramResult> =
        useService(options, OpenOptions(progress = listener)) { service ->
            val target = node?.let(options::fromFolder)
            if (kind == DiagramKind.SYMBOL && target != null) {
                service.answerAbout(target, options.askedFrom()) { id -> service.diagram(request(id)) }
            } else {
                service.requireIndex()
                NodeAnswer.Found(
                    service.diagram(request(target)),
                    service.workspaceLocations(),
                    service.indexLevel(),
                )
            }
        }

    private fun request(target: String?) = DiagramRequest(kind, target, depth, links, characters)

    private fun show(answer: NodeAnswer.Found<DiagramResult>, format: DiagramFormat, options: ResolvedOptions) {
        when (val result = answer.value) {
            is DiagramResult.Built ->
                echoResult(format.render(result.diagram, answer.level, options.parentIndex()), format.output)

            is DiagramResult.Ambiguous -> reportAmbiguity(ambiguity(result, answer.level), options)

            is DiagramResult.NotFound -> echoResult(notFound(result, answer.level, options), format.output)

            is DiagramResult.NotSupported -> throw UsageError(DiagramMessages.notSupported(result.symbol))

            is DiagramResult.BudgetTooSmall ->
                throw UsageError(DiagramMessages.tooSmall(characters, result.minimumCharacters))
        }
    }

    private fun notFound(result: DiagramResult.NotFound, level: ScanLevel?, options: ResolvedOptions): String =
        if (options.format == OutputFormat.JSON) {
            ServiceJson.encode(NodeResolution(result.node.orEmpty(), emptyList()), level, options.parentIndex())
        } else {
            DiagramMessages.notFound(kind, result.node)
        }

    private fun ambiguity(result: DiagramResult.Ambiguous, level: ScanLevel?) = NodeAnswer.Ambiguous(
        NodeResolution(result.node, result.candidates.map { it.id.value }),
        level,
    )

    private companion object {
        val DEFAULTS = DiagramRequest(DiagramKind.MODULES)
    }
}
