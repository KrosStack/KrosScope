package io.github.krosstack.kscope.cli.service

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import io.github.krosstack.kscope.cli.service.entry.EntryFactory
import io.github.krosstack.kscope.cli.service.entry.Installation
import io.github.krosstack.kscope.cli.service.entry.SessionEntry
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.launch.JvmProcess
import io.github.krosstack.kscope.cli.service.launch.LaunchPlan
import io.github.krosstack.kscope.cli.service.launch.ServiceLaunch
import java.io.IOException

internal class ServiceInstallCommand(private val entries: EntryFactory, private val jvm: () -> JvmProcess) :
    CliktCommand(name = "install") {
    override fun help(context: Context): String = listOf(
        "Makes the service start with your session.",
        "It needs no administrator rights and does not start the service now; use `kscope service start` for " +
            "that. It registers one entry for you: a value of the Run key of the registry on Windows, a " +
            "systemd user unit on Linux, a launch agent on macOS. Run it again after kscope or Java moved, or " +
            "after `memory-mib` changed.",
    ).joinToString("\n\n")

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope service install",
    ).joinToString("\n")

    override fun run() {
        val directory = ServiceDirectory.of(this)
        val launch = LaunchPlan(jvm()).launch(directory, keptInFile(directory).limits)
        when (val outcome = install(entries.entryFor(directory), launch)) {
            Installation.Installed ->
                echo("Installed. The service starts with your session; start it now with `kscope service start`.")

            is Installation.Refused -> refuse(outcome.message)
        }
    }

    private fun install(entry: SessionEntry, launch: ServiceLaunch): Installation = try {
        entry.install(launch)
    } catch (failure: IOException) {
        Installation.Refused("error: ${failure.message.orEmpty()}")
    }
}
