package io.github.krosstack.kscope.cli.service

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.cli.serve.ServeClient
import io.github.krosstack.kscope.cli.serve.ServeRequest
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory

internal class ServicePageCommand : CliktCommand(name = "page") {
    override fun help(context: Context): String = listOf(
        "Prints the address of the status page of the service.",
        "Open it in a browser to see what the service does with each workspace. The page is read-only and is " +
            "served only on this machine; its address holds a token that changes each time the service starts. " +
            "The exit code is 0 when the service runs and 1 when it does not.",
    ).joinToString("\n\n")

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope service page",
    ).joinToString("\n")

    override fun run() {
        val metadata = ServiceDirectory.of(this).runningService()
        if (metadata == null) {
            echo(SERVICE_NOT_RUNNING)
            throw ProgramResult(ExitCodes.FAILURE)
        }
        val request = ServeRequest(token = metadata.token, action = "page")
        val code = ServeClient.exchange(
            metadata,
            request,
            requireCompatible = false,
            answerTimeoutMillis = CONTROL_TIMEOUT_MILLIS,
        ) { echo(it.text, trailingNewline = false, err = it.stream == "stderr") }
        if (code == null) refuseUnanswered()
        if (code != 0) throw ProgramResult(code)
    }
}
