package io.github.krosstack.kscope.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import kotlinx.serialization.Serializable

@Serializable
internal data class ClientRendering(
    val width: Int = 79,
    val colorLevel: String = "NONE",
    val stdoutIsConsole: Boolean = false,
    val stderrIsConsole: Boolean = false,
    val noColor: Boolean = false,
    val lineSeparator: String = "\n",
) {
    companion object {
        fun of(command: CliktCommand, environment: CliEnvironment): ClientRendering = ClientRendering(
            width = command.terminal.size.width,
            colorLevel = command.terminal.terminalInfo.ansiLevel.name,
            stdoutIsConsole = command.terminal.terminalInfo.outputInteractive,
            stderrIsConsole = environment.screen.isConsole,
            noColor = command.currentContext.readEnvvar("NO_COLOR") != null,
            lineSeparator = environment.lineSeparator,
        )
    }
}
