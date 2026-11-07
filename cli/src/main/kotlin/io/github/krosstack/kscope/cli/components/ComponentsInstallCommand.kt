package io.github.krosstack.kscope.cli.components

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.github.krosstack.kscope.cli.guarded
import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.components.ComponentInstaller
import io.github.krosstack.kscope.service.components.ComponentManifest
import io.github.krosstack.kscope.service.components.ComponentPlatform
import io.github.krosstack.kscope.service.components.InstallResult
import io.github.krosstack.kscope.service.components.InstallSource
import java.nio.file.Files

internal class ComponentsInstallCommand(
    private val manifest: ComponentManifest,
    private val directory: ComponentDirectory,
) : CliktCommand(name = "install") {
    private val name by argument("NAME")
    private val yes by option("--yes", help = "Approve the download without a terminal prompt.").flag()
    private val from by option("--from", help = "Read pinned files from DIR or a single ZIP FILE, without network.")
        .path(mustExist = true)
    override fun help(context: Context) = "Shows and installs a pinned, checksummed component after confirmation."

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope components install semantic --yes",
    ).joinToString("\n")

    override fun run() = guarded {
        val entry = manifest.components.singleOrNull { it.name == name }
            ?: throw UsageError("Unknown component: $name", statusCode = 2)
        val platform = ComponentPlatform.current(System.getProperty("os.name"), System.getProperty("os.arch"))
        echo(ComponentsText.render(entry, platform))
        if (!yes) {
            val terminal = System.console()
                ?: throw UsageError("Use --yes when standard input is not a terminal", statusCode = 2)
            if (!terminal.readLine("Download? [y/N] ").orEmpty().equals("y", ignoreCase = true)) throw ProgramResult(2)
        }
        val source = from?.let { if (Files.isDirectory(it)) InstallSource.Directory(it) else InstallSource.Archive(it) }
            ?: InstallSource.Network
        val result = ComponentInstaller(directory, manifest = manifest).install(entry, platform, source)
        echo(ComponentsText.render(result))
        if (result !is InstallResult.Installed && result !is InstallResult.Replaced) throw ProgramResult(1)
    }
}
