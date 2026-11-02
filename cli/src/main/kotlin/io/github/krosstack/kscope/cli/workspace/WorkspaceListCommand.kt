package io.github.krosstack.kscope.cli.workspace

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.service.registry.RepositoryRegistration
import io.github.krosstack.kscope.service.registry.RepositoryRegistry
import java.io.File

internal class WorkspaceListCommand(private val registry: () -> RepositoryRegistry) : CliktCommand(name = "list") {
    private val common by RootOptions(SharedOption.WORKSPACE_ONLY)

    override fun help(context: Context) = "Lists the repositories registered with the workspace.\n\n" +
        "Each line names a repository and its checkout, and says when the checkout is no longer there. " +
        "`kscope info` lists every repository of the index, registered or not."

    override fun helpEpilog(context: Context) = "Examples:\n  kscope workspace list"

    override fun run() {
        val workspace = resolve(common).workspace
        val states = RepositoryRegistration(workspace, registry()).list()
        if (states.isEmpty()) {
            echo("No repository is registered with the workspace at ${display(workspace.toString())}.")
            return
        }
        val width = states.maxOf { it.repository.name.length }
        echo("${states.size} registered with the workspace at ${display(workspace.toString())}:")
        states.forEach { state ->
            val missing = if (state.present) "" else "  (missing)"
            echo("  ${state.repository.name.padEnd(width)}  ${display(state.repository.root.toString())}$missing")
        }
    }

    private fun display(path: String) = path.replace(File.separatorChar, '/')
}
