package io.github.krosstack.kscope.cli.service

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import java.nio.file.Path

internal class ServiceAddCommand : CliktCommand(name = "add") {
    private val workspace: Path? by option(
        "--workspace",
        metavar = "DIR",
        help = "The workspace to keep. It must exist. Defaults to the current directory.",
    ).path(mustExist = true, canBeFile = false)

    override fun help(context: Context): String = listOf(
        "Adds a workspace to the ones the service keeps ready.",
        "The list is the file `service.toml` in your user settings (~/.kscope/service). A service that runs " +
            "takes the workspace up at once; otherwise `kscope service start` or `run` serves it.",
    ).joinToString("\n\n")

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope service add",
        "  kscope service add --workspace path/to/app",
    ).joinToString("\n")

    override fun run() {
        val directory = ServiceDirectory.of(this)
        val target = WorkspacePaths.real(workspace ?: groupWorkspace())
        val added = directory.file.exclusively {
            val kept = keptInFile(directory)
            kept.workspaces.none { WorkspacePaths.real(Path.of(it)) == target }.also {
                if (it) directory.file.write(kept.workspaces + WorkspacePaths.key(target), kept.limits)
            }
        }
        echo(if (added) "Added $target." else "$target is kept already.")
        when (reloadService(directory)) {
            ServiceReload.NOT_RUNNING -> echo(START_HINT)
            ServiceReload.UNANSWERED -> refuseUnanswered()
            ServiceReload.RELOADED -> Unit
        }
    }

    private companion object {
        const val START_HINT = "The service does not run; start it with `kscope service run`."
    }
}
