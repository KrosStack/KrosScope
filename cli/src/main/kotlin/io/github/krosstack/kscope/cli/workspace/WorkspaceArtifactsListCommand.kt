package io.github.krosstack.kscope.cli.workspace

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.service.registry.ArtifactFolderRegistration
import io.github.krosstack.kscope.service.registry.RepositoryRegistry
import java.io.File

internal class WorkspaceArtifactsListCommand(private val registry: () -> RepositoryRegistry) :
    CliktCommand(name = "list") {
    private val common by RootOptions(SharedOption.WORKSPACE_ONLY)

    override fun help(context: Context) = "Lists the folders of built archives registered with the workspace.\n\n" +
        "They are printed in the order they are looked in, and a folder that is no longer there is marked."

    override fun helpEpilog(context: Context) = "Examples:\n  kscope workspace artifacts list"

    override fun run() {
        val workspace = resolve(common).workspace
        val folders = ArtifactFolderRegistration(workspace, registry()).list()
        if (folders.isEmpty()) {
            echo("No artifact folder is registered with the workspace at ${display(workspace.toString())}.")
            return
        }
        val count = "${folders.size} " + if (folders.size == 1) "artifact folder" else "artifact folders"
        echo("$count registered with the workspace at ${display(workspace.toString())}, in lookup order:")
        folders.forEach { folder ->
            val missing = if (folder.present) "" else "  (missing)"
            echo("  ${display(folder.path.toString())}$missing")
        }
    }

    private fun display(path: String) = path.replace(File.separatorChar, '/')
}
