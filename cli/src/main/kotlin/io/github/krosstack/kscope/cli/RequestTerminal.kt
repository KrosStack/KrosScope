package io.github.krosstack.kscope.cli

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.PrintRequest
import com.github.ajalt.mordant.terminal.TerminalInfo
import com.github.ajalt.mordant.terminal.TerminalInterface
import io.github.krosstack.kscope.cli.serve.ServeOutput

internal class RequestTerminal(private val rendering: ClientRendering, private val output: ServeOutput) :
    TerminalInterface {
    override fun info(
        ansiLevel: AnsiLevel?,
        hyperlinks: Boolean?,
        outputInteractive: Boolean?,
        inputInteractive: Boolean?,
    ): TerminalInfo = TerminalInfo(
        ansiLevel ?: AnsiLevel.valueOf(rendering.colorLevel),
        false,
        rendering.stdoutIsConsole,
        false,
        rendering.stderrIsConsole,
    )

    override fun completePrintRequest(request: PrintRequest) {
        output.write(request.text + if (request.trailingLinebreak) rendering.lineSeparator else "", request.stderr)
    }

    override fun readLineOrNull(hideInput: Boolean): String? = null

    override fun getTerminalSize(): Size = Size(rendering.width, HEIGHT)

    private companion object {
        const val HEIGHT = 24
    }
}
