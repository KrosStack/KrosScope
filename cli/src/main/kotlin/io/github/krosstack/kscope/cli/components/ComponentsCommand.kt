package io.github.krosstack.kscope.cli.components

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.github.krosstack.kscope.cli.CommandGroup
import io.github.krosstack.kscope.cli.GroupSession
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.help.FormatGuide
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.components.ComponentManifest

internal class ComponentsCommand(
    manifest: ComponentManifest = ComponentManifest.shipped(),
    directory: ComponentDirectory = ComponentDirectory.resolve(System::getProperty, System::getenv),
) : CliktCommand(name = "components") {
    private val common by RootOptions(setOf(SharedOption.WORKSPACE, SharedOption.FORMAT), FormatGuide.TEXT_OR_JSON)

    init {
        subcommands(
            ComponentsListCommand(manifest, directory),
            ComponentsInstallCommand(manifest, directory),
            ComponentsRemoveCommand(directory),
            ComponentsStatusCommand(manifest, directory),
        )
    }

    override val helpTags = CommandGroup.tag(CommandGroup.INDEX)
    override val printHelpOnEmptyArgs = true
    override fun help(context: Context) = "Installs, removes and lists optional components in your user directory."

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope components list",
        "  kscope components install semantic --yes",
    ).joinToString("\n")

    override fun run() {
        currentContext.obj = GroupSession(resolve(common))
    }
}
