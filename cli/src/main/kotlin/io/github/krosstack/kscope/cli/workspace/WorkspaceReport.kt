package io.github.krosstack.kscope.cli.workspace

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.service.registry.RegistrationOutcome
import java.io.File
import java.nio.file.Path

/** Prints what registering or unregistering did, and ends with exit code 1 when nothing could be done. */
internal fun CliktCommand.report(workspace: Path, outcome: RegistrationOutcome) {
    val root = slashed(workspace)
    when (outcome) {
        is RegistrationOutcome.Registered -> {
            val path = slashed(outcome.repository.root)
            val cloned = if (outcome.cloned) "Cloned into $path and registered" else "Registered $path"
            echo("$cloned as ${outcome.repository.name} with the workspace at $root. Run `kscope scan` to index it.")
        }

        is RegistrationOutcome.AlreadyRegistered ->
            echo("${slashed(outcome.repository.root)} is registered already, as ${outcome.repository.name}.")

        is RegistrationOutcome.Removed -> echo(
            "Unregistered ${outcome.repository.name}; its checkout at ${slashed(outcome.repository.root)} stays. " +
                "Run `kscope scan` to drop it from the index.",
        )

        is RegistrationOutcome.NotRegistered -> fail("No repository named ${outcome.name} is registered with $root.")

        is RegistrationOutcome.Refused -> fail(outcome.message)
    }
}

private fun CliktCommand.fail(message: String) {
    echo(message, err = true)
    throw ProgramResult(ExitCodes.FAILURE)
}

private fun slashed(path: Path): String = path.toString().replace(File.separatorChar, '/')
