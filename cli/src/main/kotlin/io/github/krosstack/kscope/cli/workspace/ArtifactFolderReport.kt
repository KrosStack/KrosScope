package io.github.krosstack.kscope.cli.workspace

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.service.registry.FolderOutcome
import java.io.File
import java.nio.file.Path

/** Prints what registering or unregistering a folder did, and ends with exit code 1 when nothing could be done. */
internal fun CliktCommand.reportFolder(workspace: Path, outcome: FolderOutcome) {
    val root = slashed(workspace)
    when (outcome) {
        is FolderOutcome.Registered -> echo(
            "Registered ${slashed(outcome.folder)} as an artifact folder of the workspace at $root. " +
                "Run `kscope scan` to link the archives it holds.",
        )

        is FolderOutcome.AlreadyRegistered -> echo("${slashed(outcome.folder)} is registered already.")

        is FolderOutcome.Removed -> echo(
            "Unregistered ${slashed(outcome.folder)}; the folder stays. " +
                "Run `kscope scan` to drop the links read from it.",
        )

        is FolderOutcome.NotRegistered ->
            fail("${slashed(outcome.folder)} is not registered as an artifact folder of $root.")

        is FolderOutcome.Refused -> fail(outcome.message)
    }
}

private fun CliktCommand.fail(message: String) {
    echo(message, err = true)
    throw ProgramResult(ExitCodes.FAILURE)
}

private fun slashed(path: Path): String = path.toString().replace(File.separatorChar, '/')
