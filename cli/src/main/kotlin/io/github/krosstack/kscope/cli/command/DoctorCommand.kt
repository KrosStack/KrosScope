package io.github.krosstack.kscope.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.github.krosstack.kscope.cli.CommandGroup
import io.github.krosstack.kscope.cli.OutputFormat
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.guarded
import io.github.krosstack.kscope.cli.help.FormatGuide
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.requireNoDot
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.text.DoctorText
import io.github.krosstack.kscope.cli.textContext
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.doctor

internal class DoctorCommand(
    private val directory: ComponentDirectory = ComponentDirectory.resolve(System::getProperty, System::getenv),
) : CliktCommand(name = "doctor") {
    private val common by RootOptions(
        setOf(SharedOption.WORKSPACE, SharedOption.FORMAT),
        FormatGuide.TEXT_OR_JSON,
    )

    override val helpTags = CommandGroup.tag(CommandGroup.DIAGNOSE)

    override fun help(context: Context) = "Checks that what kscope needs is available.\n\n" +
        "Use it when something does not work. It looks for a suitable JDK, the Kotlin analysis worker, Git, " +
        "the Android SDK and the index of the workspace, and reports the text encodings in use. It never runs " +
        "the project's build. It is a report, so the exit code is 0 unless the index cannot be read."

    override fun helpEpilog(context: Context) = "Examples:\n  kscope doctor\n  kscope doctor --workspace path/to/app"

    override fun run() {
        val options = resolve(common)
        requireNoDot(options.format)
        val result = guarded { KrosScope.open(options.workspace).use { it.doctor(directory) } }
        val rendered = if (options.format == OutputFormat.JSON) {
            ServiceJson.encode(result)
        } else {
            DoctorText.render(result, options.environment.encodings, textContext(options))
        }
        echo(rendered)
    }
}
