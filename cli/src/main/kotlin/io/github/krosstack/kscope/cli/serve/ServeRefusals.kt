package io.github.krosstack.kscope.cli.serve

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import io.github.krosstack.kscope.cli.ExitCodes

internal object ServeRefusals {
    fun versionMismatch(command: CliktCommand, scan: Boolean) {
        command.echo(ServeClient.VERSION_NOTE, err = true)
        if (scan) throw ProgramResult(ExitCodes.FAILURE)
    }

    fun optedOutScan(command: CliktCommand): Nothing {
        command.echo("The server has to be stopped first: run `kscope serve --stop` for this workspace.", err = true)
        throw ProgramResult(ExitCodes.FAILURE)
    }

    fun unavailableScan(command: CliktCommand): Nothing {
        command.echo(
            "The server is busy or unreachable; stop it with `kscope serve --stop` for this workspace.",
            err = true,
        )
        throw ProgramResult(ExitCodes.FAILURE)
    }
}
