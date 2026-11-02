package io.github.krosstack.kscope.cli.workspace

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.github.krosstack.kscope.cli.CommandGroup
import io.github.krosstack.kscope.cli.GroupSession
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.service.registry.RepositoryRegistry

internal class WorkspaceCommand(
    property: (String) -> String? = System::getProperty,
    registry: () -> RepositoryRegistry = { RepositoryRegistry.ofThisMachine() },
) : CliktCommand(name = "workspace") {
    private val common by RootOptions(SharedOption.WORKSPACE_ONLY)

    init {
        subcommands(
            WorkspaceAddCommand(registry),
            WorkspaceListCommand(registry),
            WorkspaceRemoveCommand(registry),
            WorkspaceArtifactsCommand(property, registry),
        )
    }

    override val helpTags = CommandGroup.tag(CommandGroup.INDEX)

    override val printHelpOnEmptyArgs = true

    override fun help(context: Context): String = listOf(
        "Registers repositories from anywhere on disk with the workspace.",
        "A registered repository is indexed together with the workspace by every scan, so that references, " +
            "impact and context reach into it. Repositories side by side below a root that is not itself a " +
            "repository are found without registering. The list is kept in your user settings " +
            "(~/.kscope/service), not in the project, and nothing is built. `artifacts` registers folders of " +
            "built libraries instead.",
    ).joinToString("\n\n")

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope workspace add ../native-core",
        "  kscope workspace add https://github.com/example/shared-kit.git --ref v2.1.0",
        "  kscope workspace list",
        "  kscope workspace remove native-core",
        "  kscope workspace artifacts add --maven-local",
    ).joinToString("\n")

    override fun run() {
        currentContext.obj = GroupSession(resolve(common))
    }
}
