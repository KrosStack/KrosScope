package io.github.krosstack.kscope.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.parsers.CommandLineParser
import com.github.ajalt.mordant.terminal.Terminal

fun main(args: Array<String>) {
    val terminal = Terminal()
    val streams = ProcessStreams.detect(stdoutIsConsole = terminal.terminalInfo.outputInteractive)
    streams.install()
    val command: CliktCommand = KscopeCommand(CliEnvironment.system(streams, terminal))
    CommandLineParser.main(command) { parseWithExitCodes(args.asList()) }
}

/** Parses and runs the command, reporting usage errors with the exit code of this tool instead of Clikt's. */
internal fun CoreCliktCommand.parseWithExitCodes(argv: List<String>) {
    try {
        if (this is KscopeCommand) parseResolved(argv) else parse(argv)
    } catch (error: UsageError) {
        echoFormattedHelp(error)
        throw ProgramResult(ExitCodes.USAGE)
    }
}
