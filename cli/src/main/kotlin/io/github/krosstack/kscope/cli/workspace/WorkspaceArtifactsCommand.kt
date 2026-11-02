package io.github.krosstack.kscope.cli.workspace

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.github.krosstack.kscope.cli.GroupSession
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.service.registry.RepositoryRegistry

internal class WorkspaceArtifactsCommand(property: (String) -> String?, registry: () -> RepositoryRegistry) :
    CliktCommand(name = "artifacts") {
    private val common by RootOptions(SharedOption.WORKSPACE_ONLY)

    init {
        subcommands(
            WorkspaceArtifactsAddCommand(property, registry),
            WorkspaceArtifactsRemoveCommand(registry),
            WorkspaceArtifactsListCommand(registry),
        )
    }

    override val printHelpOnEmptyArgs = true

    override fun help(context: Context): String = listOf(
        "Registers folders of built JAR and AAR files for a scan to read.",
        "Use it when a module of one repository depends on a library that another repository of the workspace " +
            "builds: a scan finds the library's archives in these folders, by Maven coordinate, and links the " +
            "module to the one whose sources they were built from. Nothing outside the workspace is read unless " +
            "you register it here. The list is kept in your user settings (~/.kscope/service), not in the " +
            "project, and no build runs.",
    ).joinToString("\n\n")

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope workspace artifacts add --maven-local",
        "  kscope workspace artifacts add ~/.gradle",
        "  kscope workspace artifacts list",
        "  kscope workspace artifacts remove ~/.gradle",
    ).joinToString("\n")

    override fun run() {
        currentContext.obj = GroupSession(resolve(common))
    }
}
