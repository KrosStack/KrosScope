package io.github.krosstack.kscope.cli.service

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.launch.JvmProcess
import io.github.krosstack.kscope.cli.service.launch.LaunchPlan
import io.github.krosstack.kscope.cli.service.launch.ServiceStarter
import io.github.krosstack.kscope.cli.service.launch.StartOutcome

internal class ServiceStartCommand(private val starter: ServiceStarter, private val jvm: () -> JvmProcess) :
    CliktCommand(name = "start") {
    override fun help(context: Context): String = listOf(
        "Starts the service in the background and returns when it runs.",
        "It is `kscope service run --log` made independent of this terminal: it writes `service.log` next to " +
            "`service.toml`. When the service runs already it says so and does nothing. If it has not started " +
            "within 30 seconds, the exit code is 1.",
    ).joinToString("\n\n")

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope service start",
    ).joinToString("\n")

    override fun run() {
        val directory = ServiceDirectory.of(this)
        val running = directory.runningService()
        if (running != null) {
            echo("The service runs already: pid ${running.pid}.")
            return
        }
        val launch = LaunchPlan(jvm()).launch(directory, keptInFile(directory).limits)
        when (val outcome = starter.start(directory, launch)) {
            is StartOutcome.Started -> echo("The service runs: pid ${outcome.pid}.")
            is StartOutcome.NotStarted -> refuse(notStarted(outcome, directory))
        }
    }

    private fun notStarted(outcome: StartOutcome.NotStarted, directory: ServiceDirectory): String {
        val reason = outcome.reason?.takeIf { it.isNotBlank() }?.let { "error: $it\n" }.orEmpty()
        return "${reason}The service did not start; see ${directory.logFile}."
    }
}
