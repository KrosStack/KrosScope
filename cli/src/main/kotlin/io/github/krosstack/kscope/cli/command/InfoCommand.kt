package io.github.krosstack.kscope.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import io.github.krosstack.kscope.cli.CliSession
import io.github.krosstack.kscope.cli.ClientPaths
import io.github.krosstack.kscope.cli.CommandGroup
import io.github.krosstack.kscope.cli.OutputFormat
import io.github.krosstack.kscope.cli.ResolvedOptions
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.forward
import io.github.krosstack.kscope.cli.guarded
import io.github.krosstack.kscope.cli.help.FormatGuide
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.output.echoResult
import io.github.krosstack.kscope.cli.requireIndex
import io.github.krosstack.kscope.cli.requireNoDot
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.text.InfoText
import io.github.krosstack.kscope.cli.text.StructureText
import io.github.krosstack.kscope.cli.textContext
import io.github.krosstack.kscope.cli.useService
import io.github.krosstack.kscope.service.DEFAULT_STRUCTURE_DEPTH
import io.github.krosstack.kscope.service.InfoResult
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.describeStructure

internal class InfoCommand : CliktCommand(name = "info") {
    private val common by RootOptions(
        setOf(SharedOption.WORKSPACE, SharedOption.FORMAT, SharedOption.NO_SERVER),
        FormatGuide.TEXT_OR_JSON,
    )
    private val structure by option(
        "--structure",
        help = "Describe the directory itself instead of the index: its Git checkout and worktrees, Gradle " +
            "builds, other project markers and nested repositories. It needs no index, changes nothing and runs " +
            "nothing from the directory.",
    ).flag()
    private val structureDepth by option(
        "--structure-depth",
        metavar = "N",
        help = "With --structure, how many directory levels below the workspace to inspect, from 0 to " +
            "$MAX_STRUCTURE_DEPTH.",
    ).int().restrictTo(0, MAX_STRUCTURE_DEPTH).default(DEFAULT_STRUCTURE_DEPTH)

    override val helpTags = CommandGroup.tag(CommandGroup.INSPECT)

    override fun help(context: Context) = "Summarizes what the index of the workspace holds.\n\n" +
        "Shows how many files, modules, symbols and relationships were indexed and how well the relationships " +
        "were resolved. It only reads the index and changes nothing. Run `kscope scan` first.\n\n" +
        "With --structure it describes the directory itself instead, before anything is indexed: whether it is " +
        "one build, a repository with several builds or projects, or a directory holding several repositories."

    override fun helpEpilog(context: Context) =
        "Examples:\n  kscope info\n  kscope info --format json\n  kscope info --structure\n" +
            "  kscope info --structure --structure-depth 2 --format json"

    override fun run() {
        val options = resolve(common)
        requireNoDot(options.format)
        if (structure) {
            val session = currentContext.parent?.obj as? CliSession
            session?.execution?.refuseStructure(this)
            echoResult(renderStructure(options), options.format)
            return
        }
        forward(options)
        val result = guarded {
            useService(options) {
                it.requireIndex()
                it.info()
            }
        }
        echoResult(
            render(result.copy(indexLocation = ClientPaths.indexLocation(options.workspace)), options),
            options.format,
        )
    }

    private fun renderStructure(options: ResolvedOptions): String {
        val result = guarded { KrosScope.describeStructure(options.workspace, structureDepth) }
        return when (options.format) {
            OutputFormat.JSON -> ServiceJson.encode(result)
            else -> StructureText.render(result, textContext(options))
        }
    }

    private fun render(result: InfoResult, options: ResolvedOptions) = when (options.format) {
        OutputFormat.JSON -> ServiceJson.encode(result)
        else -> InfoText.render(result, textContext(options))
    }

    private companion object {
        const val MAX_STRUCTURE_DEPTH = 20
    }
}
