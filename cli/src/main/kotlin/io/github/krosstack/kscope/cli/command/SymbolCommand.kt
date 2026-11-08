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
import io.github.krosstack.kscope.cli.text.SymbolText
import io.github.krosstack.kscope.cli.textContext
import io.github.krosstack.kscope.cli.useService
import io.github.krosstack.kscope.cli.withProgress
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ServiceJson

internal class SymbolCommand : CliktCommand(name = "symbol") {
    private val common by RootOptions(SharedOption.PRESENTED_QUERY, FormatGuide.PLAIN_TEXT_TOO)
    private val query by argument(
        "NAME",
        help = "A simple or qualified name, a symbol id, or a file location such as path/File.kt:42.",
    )

    override val helpTags = CommandGroup.tag(CommandGroup.INSPECT)

    override fun help(context: Context) = "Finds classes, functions and other declarations by name.\n\n" +
        "Exact matches come first, then the closest others. If several declarations share the name, kscope lists " +
        "each candidate and exits with code 2; run it again with one of them, or with a file location, to pick. " +
        "Run `kscope scan` first.\n\n" +
        "Sections for --sections: ${SymbolText.SECTIONS.joinToString(", ")}."

    override fun helpEpilog(context: Context) = listOf(
        "Examples:",
        "  kscope symbol TaskRepository",
        "  kscope symbol path/to/File.kt:42 --detail brief",
    ).joinToString("\n")

    override fun run() {
        val options = resolve(common, presentation = true)
        requireNoDot(options.format)
        requireSections(options, SymbolText.SECTIONS)
        forward(options)
        val answer = guarded {
            withProgress(options) { listener ->
                useService(options, OpenOptions(progress = listener)) { scope ->
                    scope.answerAbout(query, scope::symbol)
                }
            }
        }
        when (answer) {
            is NodeAnswer.Ambiguous -> reportAmbiguity(answer, options.format)

            is NodeAnswer.Found -> echoResult(
                if (options.format == OutputFormat.JSON) {
                    ServiceJson.encode(answer.value, answer.level)
                } else {
                    SymbolText.render(answer.value, query, textContext(options, answer.locations))
                },
                options.format,
            )
        }
    }
}
