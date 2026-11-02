package io.github.krosstack.kscope.cli.service

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import java.nio.file.Path

internal class ServiceRemoveCommand : CliktCommand(name = "remove") {
    private val workspace: Path? by option(
        "--workspace",
        metavar = "DIR",
        help = "The workspace to drop. It need not exist any more. Defaults to the current directory.",
    ).path(mustExist = false, canBeFile = false)

    override fun help(context: Context): String = listOf(
        "Removes a workspace from the ones the service keeps ready.",
        "A service that runs stops serving the workspace once it has finished the request it is running.",
    ).joinToString("\n\n")

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope service remove",
        "  kscope service remove --workspace path/to/app",
    ).joinToString("\n")

    override fun run() {
        val directory = ServiceDirectory.of(this)
        val target = WorkspacePaths.real(workspace ?: groupWorkspace())
        val removed = directory.file.exclusively {
            val kept = keptInFile(directory)
            val matching = kept.workspaces.filter { WorkspacePaths.real(Path.of(it)) == target }.toSet()
            if (matching.isNotEmpty()) directory.file.write(kept.workspaces - matching, kept.limits)
            matching.isNotEmpty()
        }
        echo(if (removed) "Removed $target." else "$target is not kept.")
        if (reloadService(directory) == ServiceReload.UNANSWERED) refuseUnanswered()
    }
}
