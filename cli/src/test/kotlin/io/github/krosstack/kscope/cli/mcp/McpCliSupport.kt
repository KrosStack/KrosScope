package io.github.krosstack.kscope.cli.mcp

import com.github.ajalt.mordant.terminal.Terminal
import io.github.krosstack.kscope.cli.CliEnvironment
import io.github.krosstack.kscope.cli.ClientRendering
import io.github.krosstack.kscope.cli.CommandExecution
import io.github.krosstack.kscope.cli.EncodingReport
import io.github.krosstack.kscope.cli.KscopeCommand
import io.github.krosstack.kscope.cli.RequestTerminal
import io.github.krosstack.kscope.cli.parseWithExitCodes
import io.github.krosstack.kscope.cli.progress.ProgressScreen
import io.github.krosstack.kscope.cli.progress.TickerFactory
import io.github.krosstack.kscope.cli.serve.ServeOutput
import io.github.krosstack.kscope.cli.serve.ServeResponse
import io.github.krosstack.kscope.cli.serve.ServeWire
import java.io.StringWriter
import java.nio.file.Path

internal fun mcpCommandStdout(root: Path, arguments: List<String>): String {
    val capture = StringWriter()
    val terminal = Terminal(
        width = 79,
        terminalInterface = RequestTerminal(
            ClientRendering(lineSeparator = System.lineSeparator()),
            ServeOutput(capture.buffered()),
        ),
    )
    val environment = CliEnvironment(
        ProgressScreen(79, false) {},
        System::nanoTime,
        TickerFactory.NONE,
        EncodingReport.DETACHED,
        terminal,
    )
    KscopeCommand(environment, CommandExecution(root, scanRunner = stableMcpScan)).parseWithExitCodes(
        listOf("--no-server", "--progress", "never") + arguments,
    )
    return capture.toString().lineSequence().filter { it.isNotEmpty() }
        .map { ServeWire.json.decodeFromString<ServeResponse>(it) }
        .filter { it.stream == "stdout" }.joinToString("") { it.text }
}
