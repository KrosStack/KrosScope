package io.github.krosstack.kscope.cli.workspace

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.service.registry.RepositoryRegistration
import io.github.krosstack.kscope.service.registry.RepositoryRegistry

internal class WorkspaceAddCommand(private val registry: () -> RepositoryRegistry) : CliktCommand(name = "add") {
    private val common by RootOptions(SharedOption.WORKSPACE_ONLY)
    private val location by argument(
        "LOCATION",
        help = "The repository to register: a directory anywhere in its work tree, or a Git URL to clone first.",
    )
    private val name by option(
        "--name",
        metavar = "NAME",
        help = "The name its files are printed under. Defaults to the folder name of the checkout or of the URL.",
    )
    private val ref by option("--ref", metavar = "REF", help = "With a Git URL: the branch or tag to check out.")
    private val directory by option(
        "--dir",
        metavar = "DIR",
        help = "With a Git URL: the directory to clone into. Defaults to one beside the workspace root.",
    ).path(canBeFile = false)

    override fun help(context: Context) = "Registers a repository with the workspace.\n\n" +
        "The repository must be a Git work tree with a commit, outside the workspace. A Git URL is cloned first " +
        "with your Git. The next scan indexes the repository under its name; nothing is scanned or built now."

    override fun helpEpilog(context: Context) = listOf(
        "Examples:",
        "  kscope workspace add ../native-core",
        "  kscope workspace add D:/src/web --name web-assets",
        "  kscope workspace add git@github.com:example/shared-kit.git --ref v2.1.0",
    ).joinToString("\n")

    override fun run() {
        val workspace = resolve(common).workspace
        report(workspace, RepositoryRegistration(workspace, registry()).add(location, name, ref, directory))
    }
}
