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
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.components.ComponentManifest

internal class ComponentsStatusCommand(
    private val manifest: ComponentManifest,
    private val directory: ComponentDirectory,
) : CliktCommand(name = "status") {
    private val common by RootOptions(setOf(SharedOption.FORMAT), FormatGuide.TEXT_OR_JSON)
    override fun help(context: Context) = "Lists installed components, their directories and release version matches."

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope components status",
        "  kscope components status --format json",
    ).joinToString("\n")

    override fun run() = guarded {
        val format = resolve(common).format
        if (format !in setOf(OutputFormat.TEXT, OutputFormat.JSON)) {
            throw UsageError("Use --format text or json", statusCode = 2)
        }
        echo(ComponentsText.render(directory.installed(), manifest, format == OutputFormat.JSON))
    }
}
