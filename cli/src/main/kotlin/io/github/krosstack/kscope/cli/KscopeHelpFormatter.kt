package io.github.krosstack.kscope.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.output.HelpFormatter.ParameterHelp
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.widgets.Text

/** The groups in which the top-level help lists the commands, in the order they are shown. */
internal enum class CommandGroup(val heading: String) {
    INDEX("Build the index"),
    ASK("Ask about a task"),
    INSPECT("Inspect the code"),
    SERVE("Keep it ready"),
    DIAGNOSE("Diagnose"),
    ;

    companion object {
        const val TAG = "group"

        fun tag(group: CommandGroup): Map<String, String> = mapOf(TAG to group.name)
    }
}

/**
 * Lists the commands by group, puts the options of a command before the common ones, keeps the line breaks of the
 * examples at the end of a help page and shows defaults.
 */
internal class KscopeHelpFormatter(context: Context) : MordantHelpFormatter(context, showDefaultValues = true) {
    override fun renderEpilog(epilog: String): Widget = Text(epilog, whitespace = Whitespace.PRE)

    override fun shouldShowTag(tag: String, value: String): Boolean =
        tag != CommandGroup.TAG && super.shouldShowTag(tag, value)

    override fun collectParameterSections(parameters: List<ParameterHelp>): List<RenderedSection<Widget>> {
        val commands = parameters.filterIsInstance<ParameterHelp.Subcommand>()
        val (common, own) = parameters.filterNot { it is ParameterHelp.Subcommand }.partition(::isCommon)
        return renderOptions(own) + renderOptions(common) + renderArguments(own) + groupedCommands(commands)
    }

    private val groupNames = CommandGroup.entries.map { it.name }

    private fun isCommon(parameter: ParameterHelp): Boolean =
        parameter is ParameterHelp.Group || (parameter is ParameterHelp.Option && parameter.groupName != null)

    private fun groupedCommands(commands: List<ParameterHelp.Subcommand>): List<RenderedSection<Widget>> {
        val groups = CommandGroup.entries.mapNotNull { group ->
            val rows = commands.filter { it.tags[CommandGroup.TAG] == group.name }.map {
                DefinitionRow(styleSubcommandName(it.name), renderParameterHelpText(it.help, it.tags))
            }
            rows.takeIf { it.isNotEmpty() }?.let {
                RenderedSection(styleSectionTitle(renderSectionTitle(group.heading)), buildParameterList(it))
            }
        }
        val ungrouped = commands.filter { it.tags[CommandGroup.TAG] !in groupNames }
        return groups + renderCommands(ungrouped)
    }
}
