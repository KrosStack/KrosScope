package io.github.krosstack.kscope.cli.service

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory

internal class ServiceStopCommand : CliktCommand(name = "stop") {
    override fun help(context: Context): String = listOf(
        "Stops the service that runs for you.",
        "Each workspace finishes the request it is running, then the service ends. Without a running service " +
            "it says so and exits with 0.",
    ).joinToString("\n\n")

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope service stop",
    ).joinToString("\n")

    override fun run() {
        val metadata = ServiceDirectory.of(this).runningService()
        if (metadata == null) {
            echo(SERVICE_NOT_RUNNING)
        } else if (stopService(metadata)) {
            echo("The service stopped.")
        } else {
            refuseUnanswered()
        }
    }
}
