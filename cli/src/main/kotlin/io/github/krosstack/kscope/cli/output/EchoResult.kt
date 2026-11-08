package io.github.krosstack.kscope.cli.output

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import io.github.krosstack.kscope.cli.OutputFormat

internal fun CliktCommand.echoResult(message: String, format: OutputFormat) {
    if (format == OutputFormat.JSON || format == OutputFormat.DOT) {
        terminal.rawPrint(message)
        terminal.println()
    } else {
        echo(message)
    }
}
