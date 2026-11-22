package io.github.krosstack.kscope.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.github.krosstack.kscope.cli.CommandGroup
import io.github.krosstack.kscope.cli.NodeAnswer
import io.github.krosstack.kscope.cli.OutputFormat
import io.github.krosstack.kscope.cli.ResolvedOptions
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.answerAbout
import io.github.krosstack.kscope.cli.forward
import io.github.krosstack.kscope.cli.freshness.Staleness
import io.github.krosstack.kscope.cli.freshness.warnBehind
import io.github.krosstack.kscope.cli.guarded
import io.github.krosstack.kscope.cli.help.FormatGuide
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.lookup.askedFrom
import io.github.krosstack.kscope.cli.lookup.fromFolder
import io.github.krosstack.kscope.cli.lookup.parentIndex
import io.github.krosstack.kscope.cli.noteChoice
import io.github.krosstack.kscope.cli.output.echoResult
import io.github.krosstack.kscope.cli.reportAmbiguity
import io.github.krosstack.kscope.cli.requireNoDiagramFormat
import io.github.krosstack.kscope.cli.resolveReading
import io.github.krosstack.kscope.cli.text.ImpactText
import io.github.krosstack.kscope.cli.textContext
import io.github.krosstack.kscope.cli.useService
import io.github.krosstack.kscope.cli.withProgress
import io.github.krosstack.kscope.retrieval.ImpactResult
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ServiceJson

internal class ImpactCommand : CliktCommand(name = "impact") {
    private val common by RootOptions(SharedOption.QUERY, FormatGuide.PLAIN_TEXT_TOO)
    private val node by argument(
        "TARGET",
        help = "A symbol name or id, a module path such as :core:model, or a file path relative to the workspace.",
    )

    private val staleness = Staleness(this)

    override val helpTags = CommandGroup.tag(CommandGroup.INSPECT)

    override fun help(context: Context) = "Lists what a change to a symbol, file or module can affect.\n\n" +
        "It follows the code that uses the target, nearest first, and names the modules and tests involved. " +
        "Every entry says how certain the link is. Run `kscope scan` first."

    override fun helpEpilog(context: Context) =
        "Examples:\n  kscope impact path/to/Task.kt\n  kscope impact :core:model --format json"

    override fun run() {
        val options = resolveReading(common)
        requireNoDiagramFormat(options.format)
        forward(options)
        val target = options.fromFolder(node)
        val answer = guarded {
            withProgress(options) { listener ->
                useService(options, OpenOptions(progress = listener)) { scope ->
                    staleness.read(scope, options)
                    scope.answerAbout(target, options.askedFrom(), scope::impact)
                }
            }
        }
        noteChoice(answer, target, options)
        when (answer) {
            is NodeAnswer.Ambiguous -> reportAmbiguity(answer, options)
            is NodeAnswer.Found -> show(answer, target, options)
        }
    }

    private fun show(answer: NodeAnswer.Found<ImpactResult>, target: String, options: ResolvedOptions) {
        val behind = staleness.behind()
        echoResult(
            if (options.format == OutputFormat.JSON) {
                ServiceJson.encode(answer.value, answer.level, options.parentIndex(), behind)
            } else {
                ImpactText.render(answer.value, target, textContext(options, answer.locations))
            },
            options.format,
        )
        warnBehind(behind, options.format)
    }
}
