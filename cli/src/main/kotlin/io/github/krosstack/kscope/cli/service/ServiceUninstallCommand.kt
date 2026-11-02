package io.github.krosstack.kscope.cli.service

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import io.github.krosstack.kscope.cli.service.entry.EntryFactory
import io.github.krosstack.kscope.cli.service.entry.Removal
import io.github.krosstack.kscope.cli.service.entry.SessionEntry
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import java.io.IOException

internal class ServiceUninstallCommand(private val entries: EntryFactory) : CliktCommand(name = "uninstall") {
    override fun help(context: Context): String = listOf(
        "Removes the entry that starts the service with your session.",
        "It also stops the service when it runs, and removes everything `kscope service install` wrote. " +
            "`service.toml` stays. Without an entry it says so and exits with 0.",
    ).joinToString("\n\n")

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope service uninstall",
    ).joinToString("\n")

    override fun run() {
        val directory = ServiceDirectory.of(this)
        when (val outcome = uninstall(entries.entryFor(directory))) {
            Removal.NotInstalled -> echo("The service is not installed.")

            is Removal.Refused -> refuse(outcome.message)

            Removal.Removed -> {
                val stopped = directory.runningService()?.let(::stopService) ?: true
                echo("Uninstalled.")
                if (!stopped) refuseUnanswered()
            }
        }
    }

    private fun uninstall(entry: SessionEntry): Removal = try {
        entry.uninstall()
    } catch (failure: IOException) {
        Removal.Refused("error: ${failure.message.orEmpty()}")
    }
}
