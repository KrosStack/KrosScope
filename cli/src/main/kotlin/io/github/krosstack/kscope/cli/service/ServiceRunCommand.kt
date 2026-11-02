package io.github.krosstack.kscope.cli.service

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.home.ServiceRefusal
import io.github.krosstack.kscope.cli.service.host.ServiceInstance
import io.github.krosstack.kscope.cli.service.host.ServiceSettings
import io.github.krosstack.kscope.cli.service.host.WorkspaceState
import io.github.krosstack.kscope.cli.service.log.ConsoleOutput
import io.github.krosstack.kscope.cli.service.log.ServiceLog
import io.github.krosstack.kscope.cli.service.log.ServiceOutput
import java.io.IOException
import java.nio.file.Path

internal class ServiceRunCommand : CliktCommand(name = "run") {
    private val log by option(
        "--log",
        help = "Write the status lines to service.log next to service.toml instead of standard error.",
    ).flag()

    override fun help(context: Context): String = listOf(
        "Runs the service in the foreground until it is stopped with Ctrl+C or `kscope service stop`.",
        "It serves every workspace added with `kscope service add` and keeps their indexes up to date. A scan " +
            "it starts runs a workspace's build code only if you trusted that workspace with `kscope trust`. " +
            "One scan runs at a time, and a second service of yours is refused.",
        "A workspace that nobody has used for a while is released from memory and loaded again on the next " +
            "request; `idle-minutes` and `memory-mib` in `service.toml` set when. A line on standard error says " +
            "when each workspace starts, is served or waits.",
    ).joinToString("\n\n")

    override fun helpEpilog(context: Context): String = listOf(
        "Examples:",
        "  kscope service run",
        "  kscope service run --log",
    ).joinToString("\n")

    override fun run() {
        val directory = ServiceDirectory.of(this)
        val output: ServiceOutput = if (log) ServiceLog(directory.logFile) else ConsoleOutput { echo(it, err = true) }
        open(directory, output).use { service ->
            output.begin()
            val hook = Thread({
                service.requestStop()
                service.awaitTermination()
            }, "kscope-service-shutdown")
            Runtime.getRuntime().addShutdownHook(hook)
            try {
                output.line("The service runs. Stop it with Ctrl+C or `kscope service stop`.")
                service.run()
            } finally {
                removeHook(hook)
            }
        }
    }

    private fun open(directory: ServiceDirectory, output: ServiceOutput): ServiceInstance = try {
        ServiceInstance.open(directory, ServiceSettings(onChange = { path, state -> report(output, path, state) }))
    } catch (refusal: ServiceRefusal) {
        refuse(output, refusal.message.orEmpty())
    } catch (failure: IOException) {
        refuse(output, "error: ${failure.message.orEmpty()}")
    }

    private fun refuse(output: ServiceOutput, message: String): Nothing {
        output.line(message)
        throw ProgramResult(ExitCodes.FAILURE)
    }

    private fun report(output: ServiceOutput, path: Path, state: WorkspaceState) = output.line("$path: ${state.text}")

    private fun removeHook(hook: Thread) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook)
        } catch (_: IllegalStateException) {
            return
        }
    }
}
