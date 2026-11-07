package io.github.krosstack.kscope.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import io.github.krosstack.kscope.cli.help.FormatGuide
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.progress.ProgressMode
import io.github.krosstack.kscope.cli.text.format.DetailLevel
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.RetrievalStage
import java.nio.file.Path

internal enum class OutputFormat { TEXT, JSON, MARKDOWN, DOT }

/**
 * The options every command accepts, given before or after the command name. Those not in [shown] are accepted all
 * the same and left out of the help, because the command ignores or refuses them.
 */
internal class RootOptions(
    private val shown: Set<SharedOption> = SharedOption.ALL,
    formats: FormatGuide = FormatGuide.EVERYTHING,
) : OptionGroup(COMMON_OPTIONS) {
    val noMl: Boolean by option(
        "--no-ml",
        help = "Answers without learned signals; the `semantic` object of the JSON says `off`",
        hidden = hides(SharedOption.ML),
    ).flag()

    val ml: MlMode? by option(
        "--ml",
        help = "Use learned signals when installed: on or off. --no-ml wins when both are given.",
        hidden = hides(SharedOption.ML),
    ).choice("on" to MlMode.ON, "off" to MlMode.OFF)

    val stage: RetrievalStage? by option(
        "--stage",
        help = "Choose task retrieval: full runs the pipeline, lexical uses lexical ranking alone. Defaults to full.",
        hidden = hides(SharedOption.STAGE),
    ).choice("full" to RetrievalStage.FULL, "lexical" to RetrievalStage.LEXICAL)

    val workspace: Path? by option(
        "--workspace",
        metavar = "DIR",
        help = "The workspace: the project directory to work on. It must exist. Defaults to the current directory.",
        hidden = hides(SharedOption.WORKSPACE),
    ).path(mustExist = true, canBeFile = false)

    val format: OutputFormat? by option(
        "--format",
        help = formats.help,
        hidden = hides(SharedOption.FORMAT),
    ).choice(
        "text" to OutputFormat.TEXT,
        "json" to OutputFormat.JSON,
        "markdown" to OutputFormat.MARKDOWN,
        "dot" to OutputFormat.DOT,
        metavar = formats.choices,
    )

    val progress: ProgressMode? by option(
        "--progress",
        help = "How to show progress on standard error: auto draws a live line on a terminal and plain lines " +
            "elsewhere, always forces the live line, never shows nothing. Defaults to auto.",
        hidden = hides(SharedOption.PROGRESS),
    ).choice("auto" to ProgressMode.AUTO, "always" to ProgressMode.ALWAYS, "never" to ProgressMode.NEVER)

    val quiet: Boolean by option(
        "--quiet",
        "-q",
        help = "Show no progress; standard error then carries errors only.",
        hidden = hides(SharedOption.QUIET),
    ).flag()

    val detail: DetailLevel? by option(
        "--detail",
        help = "How much to print: brief fits one screen, standard is the default, full lists every relevant " +
            "row of every section. Text and markdown output only.",
        hidden = hides(SharedOption.DETAIL),
    ).choice("brief" to DetailLevel.BRIEF, "standard" to DetailLevel.STANDARD, "full" to DetailLevel.FULL)

    val sections: List<String>? by option(
        "--sections",
        metavar = "LIST",
        help = "Print only these sections, as a comma-separated list of the section names that the command " +
            "lists in its description. Text and markdown output only.",
        hidden = hides(SharedOption.SECTIONS),
    ).split(",")

    val noServer: Boolean by option(
        "--no-server",
        help = "Read the index directly instead of asking the server that `kscope serve` or `kscope service` " +
            "keeps for the workspace.",
        hidden = hides(SharedOption.NO_SERVER),
    ).flag()

    fun mlMode(inherited: RootOptions?): MlMode =
        if (noMl || inherited?.noMl == true) MlMode.OFF else ml ?: inherited?.ml ?: MlMode.ON

    private fun hides(option: SharedOption) = option !in shown

    private companion object {
        const val COMMON_OPTIONS = "Common options"
    }
}

/** What the root command hands to the subcommands: the options given before the command name, and the process. */
internal class CliSession(val defaults: RootOptions, val environment: CliEnvironment, val execution: CommandExecution)

/** What a command group hands to its subcommands: the options given to the group, which their own options override. */
internal class GroupSession(val resolved: ResolvedOptions)

internal data class ResolvedOptions(
    val workspace: Path,
    val format: OutputFormat,
    val progress: ProgressMode,
    val quiet: Boolean,
    val environment: CliEnvironment,
    val detail: DetailLevel = DetailLevel.STANDARD,
    val sections: Set<String>? = null,
    val noServer: Boolean = false,
    val ml: MlMode = MlMode.ON,
    val stage: RetrievalStage = RetrievalStage.FULL,
)

/** The `--detail` and `--sections` the user gave, the ones after the command name winning. */
private class Presentation(own: RootOptions, inherited: RootOptions?) {
    val detail: DetailLevel = own.detail ?: inherited?.detail ?: DetailLevel.STANDARD
    val sections: Set<String>? = (own.sections ?: inherited?.sections)?.map { it.trim().lowercase() }?.toSet()
    val given: Boolean = own.detail != null || inherited?.detail != null || sections != null
}

/**
 * Options given after the command name win over options given before it. [presentation] says that the command
 * honours `--detail` and `--sections`; any other command refuses them rather than ignore them.
 */
internal fun CliktCommand.resolve(own: RootOptions, presentation: Boolean = false): ResolvedOptions {
    val parent = currentContext.parent?.obj
    if (parent is GroupSession) return resolveWithin(parent.resolved, own, presentation)
    val session = parent as? CliSession
    val inherited = session?.defaults
    val shown = Presentation(own, inherited)
    val environment = session?.environment ?: CliEnvironment.detached()
    val resolved = own.mergedWith(inherited, environment, shown).copy(
        noServer = own.noServer || inherited?.noServer == true || currentContext.readEnvvar("KSCOPE_NO_SERVER") == "1",
        workspace = (own.workspace ?: inherited?.workspace ?: session?.execution?.directory ?: Path.of(""))
            .toAbsolutePath().normalize(),
    )
    if (shown.given) requirePresentation(presentation, resolved.format)
    return resolved
}

private fun CliktCommand.resolveWithin(
    group: ResolvedOptions,
    own: RootOptions,
    presentation: Boolean,
): ResolvedOptions {
    val shown = Presentation(own, null)
    val resolved = ResolvedOptions(
        workspace = (own.workspace ?: group.workspace).toAbsolutePath().normalize(),
        format = own.format ?: group.format,
        progress = own.progress ?: group.progress,
        quiet = own.quiet || group.quiet,
        environment = group.environment,
        detail = shown.detail,
        sections = shown.sections,
        noServer = own.noServer || group.noServer,
        ml = if (own.noMl) MlMode.OFF else own.ml ?: group.ml,
        stage = own.stage ?: group.stage,
    )
    if (shown.given) requirePresentation(presentation, resolved.format)
    return resolved
}

private fun RootOptions.mergedWith(
    inherited: RootOptions?,
    environment: CliEnvironment,
    shown: Presentation,
): ResolvedOptions = ResolvedOptions(
    workspace = workspace ?: inherited?.workspace ?: Path.of("").toAbsolutePath(),
    format = format ?: inherited?.format ?: OutputFormat.TEXT,
    progress = progress ?: inherited?.progress ?: ProgressMode.AUTO,
    quiet = quiet || inherited?.quiet == true,
    environment = environment,
    detail = shown.detail,
    sections = shown.sections,
    ml = mlMode(inherited),
    stage = stage ?: inherited?.stage ?: RetrievalStage.FULL,
)

private fun requirePresentation(supported: Boolean, format: OutputFormat) {
    if (!supported) throw UsageError("--detail and --sections are available for analyze, symbol and related only.")
    if (format == OutputFormat.JSON) {
        throw UsageError("--detail and --sections apply to text and markdown output; JSON always has the full result.")
    }
}

/** Refuses a `--sections` list that is empty or names a section that [known] does not hold. */
internal fun requireSections(options: ResolvedOptions, known: List<String>) {
    val requested = options.sections ?: return
    val unknown = requested.filterNot { it in known }
    if (requested.isEmpty() || "" in requested || unknown.isNotEmpty()) {
        val which = if (unknown.isEmpty()) "An empty section name" else "Unknown section ${unknown.joinToString(", ")}"
        throw UsageError("$which. The sections here are: ${known.joinToString(", ")}.")
    }
}
