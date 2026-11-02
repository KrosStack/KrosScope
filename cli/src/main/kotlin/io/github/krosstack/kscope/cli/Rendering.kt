package io.github.krosstack.kscope.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.mordant.rendering.AnsiLevel
import io.github.krosstack.kscope.cli.text.format.Emphasis
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.model.WorkspaceLocations

/**
 * The context for rendering text. Bold and colour are used only when the result is text on a terminal that supports
 * them and the user did not ask for plain output with `NO_COLOR`; captured output never holds an escape code. Long
 * paths are shortened to the width of a terminal and never in captured output, where agents need exact paths.
 */
internal fun CliktCommand.textContext(
    options: ResolvedOptions,
    locations: WorkspaceLocations = WorkspaceLocations.SINGLE,
): TextContext {
    val info = terminal.terminalInfo
    val onTerminal = options.format == OutputFormat.TEXT && info.outputInteractive
    val emphasised = onTerminal && info.ansiLevel != AnsiLevel.NONE && currentContext.readEnvvar(NO_COLOR) == null
    return TextContext(
        workspace = options.workspace,
        emphasis = Emphasis(emphasised),
        width = if (onTerminal) terminal.size.width else null,
        detail = options.detail,
        sections = options.sections,
    ).located(locations)
}

/** Only the graph command prints Graphviz DOT. */
internal fun requireNoDot(format: OutputFormat) {
    if (format == OutputFormat.DOT) throw UsageError("--format dot is available for the graph command only.")
}

private const val NO_COLOR = "NO_COLOR"
