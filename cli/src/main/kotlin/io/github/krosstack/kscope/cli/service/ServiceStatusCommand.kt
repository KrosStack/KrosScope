package io.github.krosstack.kscope.cli.service

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.cli.serve.ServeClient
import io.github.krosstack.kscope.cli.serve.ServeRequest
import io.github.krosstack.kscope.cli.service.entry.EntryFactory
import io.github.krosstack.kscope.cli.service.entry.SessionLine
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.home.ServiceLimits

internal class ServiceStatusCommand(private val entries: EntryFactory) : CliktCommand(name = "status") {
    override fun help(context: Context): String = listOf(
        "Says whether the service runs and what it does with each workspace.",
        "It also shows the limits the service keeps and whether it starts with your session. The exit code is " +
            "0 when the service runs and 1 when it does not. It does not wait for a workspace that is busy.",
    ).joinToString("\n\n")

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope service status",
    ).joinToString("\n")

    override fun run() {
        val directory = ServiceDirectory.of(this)
        val sessionLine = SessionLine.of(entries.entryFor(directory).state())
        val metadata = directory.runningService()
        if (metadata == null) {
            echo(SERVICE_NOT_RUNNING)
            echo(sessionLine)
            throw ProgramResult(ExitCodes.FAILURE)
        }
        echo("The service runs: pid ${metadata.pid}, version ${metadata.toolVersion}, started ${metadata.startTime}.")
        val request = ServeRequest(token = metadata.token, action = "status")
        var pending = true
        val answered = ServeClient.exchange(
            metadata,
            request,
            requireCompatible = false,
            answerTimeoutMillis = STATUS_TIMEOUT_MILLIS,
        ) {
            echo(it.text, trailingNewline = false, err = it.stream == "stderr")
            if (pending && it.text.startsWith(ServiceLimits.LINE_PREFIX)) {
                echo(sessionLine)
                pending = false
            }
        }
        if (answered == null) echo("The service did not answer within 5 seconds.")
        if (pending) echo(sessionLine)
    }

    private companion object {
        const val STATUS_TIMEOUT_MILLIS = 5_000
    }
}
