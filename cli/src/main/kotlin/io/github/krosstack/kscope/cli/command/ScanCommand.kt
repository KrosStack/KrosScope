package io.github.krosstack.kscope.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.github.krosstack.kscope.cli.ClientPaths
import io.github.krosstack.kscope.cli.CommandGroup
import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.cli.OutputFormat
import io.github.krosstack.kscope.cli.ResolvedOptions
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.forward
import io.github.krosstack.kscope.cli.guarded
import io.github.krosstack.kscope.cli.help.FormatGuide
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.output.echoResult
import io.github.krosstack.kscope.cli.requireNoDot
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.scanService
import io.github.krosstack.kscope.cli.text.ScanText
import io.github.krosstack.kscope.cli.textContext
import io.github.krosstack.kscope.cli.withProgress
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ProgressListener
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.ScanResult
import io.github.krosstack.kscope.service.ServiceJson

/** `scan`, and `update`, its hidden alias for scripts written when the command had that name. */
internal class ScanCommand(name: String = "scan", compatibilityAlias: Boolean = false) : CliktCommand(name = name) {
    private val common by RootOptions(
        setOf(SharedOption.WORKSPACE, SharedOption.FORMAT, SharedOption.PROGRESS, SharedOption.QUIET),
        FormatGuide.TEXT_OR_JSON,
    )
    private val rebuild by option(
        "--rebuild",
        help = "Throw the index away and read everything again. Use it when the index is unreadable or you want " +
            "a clean start.",
    ).flag()
    private val verify by option(
        "--verify",
        help = "After the scan, build a second index from scratch and compare the two; a difference exits with 3.",
    ).flag()
    private val level by option(
        "--level",
        metavar = "LEVEL",
        help = "How deep to scan: syntax reads declarations only, resolved (the default) also links references, " +
            "exact asks the project's Gradle build for its exact model. exact runs the project's own build " +
            "code, so use it only on code you trust. Without this option a scan keeps the level of the " +
            "existing index; going shallower needs --rebuild.",
    ).convert { ScanLevel.parse(it) ?: fail(levelProblem(it)) }
    private val trustBuild by option(
        "--trust-build",
        help = "The same as --level exact: runs the project's own build code, so use it only on code you trust.",
    ).flag()

    override val hiddenFromHelp = compatibilityAlias
    override val helpTags = CommandGroup.tag(CommandGroup.INDEX)
    private val alias = compatibilityAlias

    override fun help(context: Context) = if (alias) ALIAS_HELP else SCAN_HELP

    override fun helpEpilog(context: Context) = if (alias) ALIAS_EXAMPLES else SCAN_EXAMPLES

    override fun run() {
        val options = resolve(common)
        requireNoDot(options.format)
        val asked = askedLevel()
        forward(options)
        val result = guarded {
            withProgress(options) { listener ->
                scanService(options, openOptions(asked, listener), rebuild, verify)
            }
        }
        echoResult(
            render(result.copy(indexLocation = ClientPaths.indexLocation(options.workspace)), options),
            options.format,
        )
        if (result.verification?.matches == false) throw ProgramResult(ExitCodes.VERIFICATION_MISMATCH)
    }

    private fun askedLevel(): ScanLevel? {
        if (trustBuild && level != null && level != ScanLevel.EXACT) {
            val other = level?.wireName
            throw UsageError(
                "--trust-build is the same as --level exact, so it cannot be combined with --level $other.",
            )
        }
        return if (trustBuild) ScanLevel.EXACT else level
    }

    private fun openOptions(asked: ScanLevel?, listener: ProgressListener) = OpenOptions(
        trustBuild = asked == ScanLevel.EXACT,
        level = asked.takeIf { it != ScanLevel.EXACT },
        progress = listener,
    )

    private fun render(result: ScanResult, options: ResolvedOptions) = when (options.format) {
        OutputFormat.JSON -> ServiceJson.encode(result)
        else -> ScanText.render(result, textContext(options))
    }

    private companion object {
        fun levelProblem(name: String): String = "Unknown level \"$name\". The levels are $LEVELS."

        val LEVELS = ScanLevel.wireNames.joinToString(", ")

        const val SCAN_HELP = "Builds the index of the workspace, or brings it up to date.\n\n" +
            "The first scan reads every file; later scans redo only what changed, so they are quick. Run it " +
            "before any other command and again after the code changes. The index is kept in .kscope inside " +
            "the workspace. A scan runs the project's own build code only with `--level exact` or " +
            "`--trust-build`, or in a workspace you trusted with `kscope trust`."
        const val SCAN_EXAMPLES = "Examples:\n" +
            "  kscope scan\n" +
            "  kscope scan --rebuild --verify --workspace path/to/app\n" +
            "  kscope scan --level exact"
        const val ALIAS_HELP = "Compatibility alias of `scan`, for scripts that call it by its old name.\n\n" +
            "It takes the same options and does the same work. Use `kscope scan` in new scripts."
        const val ALIAS_EXAMPLES = "Example:\n  kscope update --workspace path/to/app"
    }
}
