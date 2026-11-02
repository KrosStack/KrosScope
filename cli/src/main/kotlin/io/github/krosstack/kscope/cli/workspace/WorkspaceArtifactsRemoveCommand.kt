package io.github.krosstack.kscope.cli.workspace

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.types.path
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.service.registry.ArtifactFolderRegistration
import io.github.krosstack.kscope.service.registry.RepositoryRegistry

internal class WorkspaceArtifactsRemoveCommand(private val registry: () -> RepositoryRegistry) :
    CliktCommand(name = "remove") {
    private val common by RootOptions(SharedOption.WORKSPACE_ONLY)
    private val directory by argument(
        "DIR",
        help = "The folder to unregister, as `kscope workspace artifacts list` prints it. It need not exist any more.",
    ).path()

    override fun help(context: Context) = "Unregisters a folder of built archives from the workspace.\n\n" +
        "The folder stays on disk. The next scan drops the links that were read from it."

    override fun helpEpilog(context: Context) = "Examples:\n  kscope workspace artifacts remove ~/.m2/repository"

    override fun run() {
        val workspace = resolve(common).workspace
        reportFolder(workspace, ArtifactFolderRegistration(workspace, registry()).remove(directory))
    }
}
