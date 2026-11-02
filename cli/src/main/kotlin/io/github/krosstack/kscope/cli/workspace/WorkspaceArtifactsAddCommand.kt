package io.github.krosstack.kscope.cli.workspace

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.service.registry.ArtifactFolderRegistration
import io.github.krosstack.kscope.service.registry.RepositoryRegistry

internal class WorkspaceArtifactsAddCommand(
    private val property: (String) -> String?,
    private val registry: () -> RepositoryRegistry,
) : CliktCommand(name = "add") {
    private val common by RootOptions(SharedOption.WORKSPACE_ONLY)
    private val directory by argument(
        "DIR",
        help = "The folder to read built archives from: a Maven local repository, a Gradle user home or its " +
            "caches/modules-2 folder, or any folder laid out like them.",
    ).path().optional()
    private val mavenLocal by option(
        "--maven-local",
        help = "Register .m2/repository in your home folder instead of a DIR.",
    ).flag()

    override fun help(context: Context) = "Registers a folder of built archives with the workspace.\n\n" +
        "The folder must exist. Folders are searched in the order they were added. The next scan reads from it; " +
        "nothing is scanned or built now. Your shell expands `~`; kscope does not."

    override fun helpEpilog(context: Context) = listOf(
        "Examples:",
        "  kscope workspace artifacts add --maven-local",
        "  kscope workspace artifacts add ~/.gradle",
        "  kscope workspace artifacts add D:/caches/modules-2",
    ).joinToString("\n")

    override fun run() {
        val workspace = resolve(common).workspace
        val given = directory
        val registration = ArtifactFolderRegistration(workspace, registry())
        val outcome = when {
            given != null && mavenLocal -> throw UsageError("Give either a DIR or --maven-local, not both.")
            given != null -> registration.add(given)
            mavenLocal -> registration.addMavenLocal(property)
            else -> throw UsageError("Give a DIR, or --maven-local for the Maven local repository.")
        }
        reportFolder(workspace, outcome)
    }
}
