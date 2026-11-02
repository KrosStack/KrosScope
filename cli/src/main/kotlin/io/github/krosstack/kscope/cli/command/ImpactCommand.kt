package io.github.krosstack.kscope.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.github.krosstack.kscope.cli.CommandGroup
import io.github.krosstack.kscope.cli.NodeAnswer
import io.github.krosstack.kscope.cli.OutputFormat
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.answerAbout
import io.github.krosstack.kscope.cli.forward
import io.github.krosstack.kscope.cli.guarded
import io.github.krosstack.kscope.cli.help.FormatGuide
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.reportAmbiguity
import io.github.krosstack.kscope.cli.requireNoDot
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.text.ImpactText
import io.github.krosstack.kscope.cli.textContext
import io.github.krosstack.kscope.cli.useService
import io.github.krosstack.kscope.cli.withProgress
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ServiceJson

internal class ImpactCommand : CliktCommand(name = "impact") {
    private val common by RootOptions(SharedOption.QUERY, FormatGuide.PLAIN_TEXT_TOO)
    private val node by argument(
        "TARGET",
        help = "A symbol name or id, a module path such as :core:model, or a file path relative to the workspace.",
    )

    override val helpTags = CommandGroup.tag(CommandGroup.INSPECT)

    override fun help(context: Context) = "Lists what a change to a symbol, file or module can affect.\n\n" +
        "It follows the code that uses the target, nearest first, and names the modules and tests involved. " +
        "Every entry says how certain the link is. Run `kscope scan` first."

    override fun helpEpilog(context: Context) =
        "Examples:\n  kscope impact path/to/Task.kt\n  kscope impact :core:model --format json"

    override fun run() {
        val options = resolve(common)
        requireNoDot(options.format)
        forward(options)
        val answer = guarded {
            withProgress(options) { listener ->
                useService(options, OpenOptions(progress = listener)) { scope ->
                    scope.answerAbout(node, scope::impact)
                }
            }
        }
        when (answer) {
            is NodeAnswer.Ambiguous -> reportAmbiguity(answer, options.format)

            is NodeAnswer.Found -> echo(
                if (options.format == OutputFormat.JSON) {
                    ServiceJson.encode(answer.value, answer.level)
                } else {
                    ImpactText.render(answer.value, node, textContext(options, answer.locations))
                },
            )
        }
    }
}
