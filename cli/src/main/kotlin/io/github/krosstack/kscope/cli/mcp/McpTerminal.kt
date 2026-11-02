package io.github.krosstack.kscope.cli.mcp

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.PrintRequest
import com.github.ajalt.mordant.terminal.TerminalInfo
import com.github.ajalt.mordant.terminal.TerminalInterface

internal class McpTerminal : TerminalInterface {
    private val stdout = StringBuilder()
    private val stderr = StringBuilder()
    val output: String get() = stdout.toString()
    val diagnostics: String get() = stderr.toString()

    override fun info(
        ansiLevel: AnsiLevel?,
        hyperlinks: Boolean?,
        outputInteractive: Boolean?,
        inputInteractive: Boolean?,
    ): TerminalInfo = TerminalInfo(AnsiLevel.NONE, false, false, false, false)

    override fun completePrintRequest(request: PrintRequest) {
        val target = if (request.stderr) stderr else stdout
        target.append(request.text)
        if (request.trailingLinebreak) target.append(System.lineSeparator())
    }

    override fun readLineOrNull(hideInput: Boolean): String? = null

    override fun getTerminalSize(): Size = Size(WIDTH, HEIGHT)

    companion object {
        const val WIDTH = 79
        private const val HEIGHT = 24
    }
}
