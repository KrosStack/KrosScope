package io.github.krosstack.kscope.cli.components

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.github.krosstack.kscope.cli.OutputFormat
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.guarded
import io.github.krosstack.kscope.cli.help.FormatGuide
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.output.echoResult
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.components.ComponentManifest
import io.github.krosstack.kscope.service.components.ComponentPlatform
import io.github.krosstack.kscope.service.components.listing
import io.github.krosstack.kscope.service.settings.WorkspaceSettings

internal class ComponentsListCommand(
    private val manifest: ComponentManifest,
    private val directory: ComponentDirectory,
) : CliktCommand(name = "list") {
    private val common by RootOptions(setOf(SharedOption.WORKSPACE, SharedOption.FORMAT), FormatGuide.TEXT_OR_JSON)
    override fun help(context: Context) = "Lists this release's components, licences, download sizes and states."

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope components list",
        "  kscope components list --format json",
    ).joinToString("\n")

    override fun run() = guarded {
        val options = resolve(common)
        if (options.format !in setOf(OutputFormat.TEXT, OutputFormat.JSON)) {
            throw UsageError("Use --format text or json", statusCode = 2)
        }
        val platform = ComponentPlatform.current(System.getProperty("os.name"), System.getProperty("os.arch"))
        val rows = manifest.listing(directory, platform, WorkspaceSettings.read(options.workspace))
        echoResult(ComponentsText.render(rows, options.format == OutputFormat.JSON, platform), options.format)
    }
}
