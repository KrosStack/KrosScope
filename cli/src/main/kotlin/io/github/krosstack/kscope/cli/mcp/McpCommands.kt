package io.github.krosstack.kscope.cli.mcp

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.mordant.terminal.Terminal
import io.github.krosstack.kscope.cli.CliEnvironment
import io.github.krosstack.kscope.cli.CommandExecution
import io.github.krosstack.kscope.cli.EncodingReport
import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.cli.KscopeCommand
import io.github.krosstack.kscope.cli.parseWithExitCodes
import io.github.krosstack.kscope.cli.progress.ProgressScreen
import io.github.krosstack.kscope.cli.progress.TickerFactory
import java.io.Writer
import java.nio.file.Path

internal class McpCommands(
    private val workspace: Path,
    private val settings: McpSettings,
    private val error: Writer = Writer.nullWriter(),
) {
    fun execute(arguments: List<String>): McpToolResult {
        val capture = McpTerminal()
        val environment = CliEnvironment(
            ProgressScreen(McpTerminal.WIDTH, false) {
                error.write(it)
                error.flush()
            },
            System::nanoTime,
            TickerFactory.NONE,
            EncodingReport.DETACHED,
            Terminal(width = McpTerminal.WIDTH, terminalInterface = capture),
        )
        val command = KscopeCommand(environment, CommandExecution(workspace, scanRunner = settings.scanRunner))
        command.configureContext { readArgumentFile = null }
        val flags = listOf("--workspace", workspace.toString(), "--progress", "never") +
            if (settings.noServer) listOf("--no-server") else emptyList()
        val code = runCatching {
            command.parseWithExitCodes(flags + arguments)
            0
        }.getOrElse { failure -> failureCode(failure, command) }
        error.write(capture.diagnostics)
        error.flush()
        return McpToolResult(resultText(capture), code != 0)
    }

    private fun resultText(capture: McpTerminal): String = when {
        capture.diagnostics.isEmpty() -> capture.output

        capture.output.isEmpty() -> capture.diagnostics

        else -> {
            val separator = if (capture.output.endsWith('\n')) "\n" else "\n\n"
            capture.output + separator + capture.diagnostics
        }
    }

    private fun failureCode(failure: Throwable, command: KscopeCommand): Int = when (failure) {
        is ProgramResult -> failure.statusCode

        is CliktError -> {
            command.echoFormattedHelp(failure)
            failure.statusCode
        }

        else -> {
            command.echo("error: ${failure.message.orEmpty()}", err = true)
            ExitCodes.FAILURE
        }
    }
}
