package io.github.krosstack.kscope.cli.workspace

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.service.registry.RepositoryRegistration
import io.github.krosstack.kscope.service.registry.RepositoryRegistry

internal class WorkspaceRemoveCommand(private val registry: () -> RepositoryRegistry) : CliktCommand(name = "remove") {
    private val common by RootOptions(SharedOption.WORKSPACE_ONLY)
    private val name by argument(
        "NAME",
        help = "The name of the registered repository, as `kscope workspace list` prints it.",
    )

    override fun help(context: Context) = "Unregisters a repository from the workspace.\n\n" +
        "Its checkout stays on disk. The next scan drops its files from the index."

    override fun helpEpilog(context: Context) = "Examples:\n  kscope workspace remove native-core"

    override fun run() {
        val workspace = resolve(common).workspace
        report(workspace, RepositoryRegistration(workspace, registry()).remove(name))
    }
}
