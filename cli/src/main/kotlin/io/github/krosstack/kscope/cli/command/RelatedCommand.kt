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
import io.github.krosstack.kscope.cli.output.echoResult
import io.github.krosstack.kscope.cli.reportAmbiguity
import io.github.krosstack.kscope.cli.requireNoDot
import io.github.krosstack.kscope.cli.requireSections
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.text.RelatedText
import io.github.krosstack.kscope.cli.textContext
import io.github.krosstack.kscope.cli.useService
import io.github.krosstack.kscope.cli.withProgress
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ServiceJson

internal class RelatedCommand : CliktCommand(name = "related") {
    private val common by RootOptions(SharedOption.PRESENTED_QUERY, FormatGuide.PLAIN_TEXT_TOO)
    private val node by argument(
        "TARGET",
        help = "A symbol name or id, a module path such as :core:model, or a file path relative to the workspace.",
    )

    override val helpTags = CommandGroup.tag(CommandGroup.INSPECT)

    override fun help(context: Context) = "Lists the files and symbols closest to a symbol, file or module.\n\n" +
        "Closeness follows the relationships recorded in the index, so it finds collaborators that share no " +
        "words with the target. A module brings in the files it owns. Weak matches are left out. Run " +
        "`kscope scan` first.\n\n" +
        "Sections for --sections: ${RelatedText.SECTIONS.joinToString(", ")}."

    override fun helpEpilog(context: Context) = listOf(
        "Examples:",
        "  kscope related path/to/Task.kt",
        "  kscope related :data:db --detail brief",
    ).joinToString("\n")

    override fun run() {
        val options = resolve(common, presentation = true)
        requireNoDot(options.format)
        requireSections(options, RelatedText.SECTIONS)
        forward(options)
        val answer = guarded {
            withProgress(options) { listener ->
                useService(options, OpenOptions(progress = listener)) { scope ->
                    scope.answerAbout(node, scope::related)
                }
            }
        }
        when (answer) {
            is NodeAnswer.Ambiguous -> reportAmbiguity(answer, options.format)

            is NodeAnswer.Found -> echoResult(
                if (options.format == OutputFormat.JSON) {
                    ServiceJson.encode(answer.value, answer.level)
                } else {
                    RelatedText.render(answer.value, node, textContext(options, answer.locations))
                },
                options.format,
            )
        }
    }
}
