package io.github.krosstack.kscope.cli.service

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.obj
import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.cli.ResolvedOptions
import io.github.krosstack.kscope.cli.serve.ServeClient
import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.serve.ServeRequest
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.home.ServiceFileReading
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal const val SERVICE_NOT_RUNNING = "The service does not run."

internal const val CONTROL_TIMEOUT_MILLIS = 10_000

private const val EXIT_SECONDS = 10L

internal enum class ServiceReload { NOT_RUNNING, RELOADED, UNANSWERED }

internal fun CliktCommand.keptInFile(directory: ServiceDirectory): ServiceFileReading.Read =
    when (val reading = directory.file.read()) {
        is ServiceFileReading.Read -> reading

        is ServiceFileReading.Unreadable -> {
            echo("error: ${reading.message}", err = true)
            throw ProgramResult(ExitCodes.FAILURE)
        }
    }

internal fun CliktCommand.reloadService(directory: ServiceDirectory): ServiceReload {
    val metadata = directory.runningService() ?: return ServiceReload.NOT_RUNNING
    val request = ServeRequest(token = metadata.token, action = "reload")
    val code = ServeClient.exchange(
        metadata,
        request,
        requireCompatible = false,
        answerTimeoutMillis = CONTROL_TIMEOUT_MILLIS,
    ) { chunk -> echo(chunk.text, trailingNewline = false, err = chunk.stream == "stderr") }
    return when {
        code == null -> ServiceReload.UNANSWERED
        code != 0 -> throw ProgramResult(code)
        else -> ServiceReload.RELOADED
    }
}

internal fun stopService(metadata: ServeMetadata): Boolean {
    val request = ServeRequest(token = metadata.token, action = "stop")
    val answer = ServeClient.exchange(
        metadata,
        request,
        requireCompatible = false,
        answerTimeoutMillis = CONTROL_TIMEOUT_MILLIS,
    ) {}
    if (answer != null) ProcessHandle.of(metadata.pid).ifPresent(::awaitExit)
    return answer != null
}

private fun awaitExit(handle: ProcessHandle) {
    if (handle.pid() == ProcessHandle.current().pid()) return
    try {
        handle.onExit().get(EXIT_SECONDS, TimeUnit.SECONDS)
    } catch (_: TimeoutException) {
        return
    }
}

internal fun CliktCommand.refuse(message: String): Nothing {
    echo(message, err = true)
    throw ProgramResult(ExitCodes.FAILURE)
}

internal fun CliktCommand.refuseUnanswered(): Nothing =
    refuse("The service did not answer; see `kscope service status`.")

internal fun CliktCommand.groupWorkspace(): Path =
    (currentContext.parent?.obj as? ResolvedOptions)?.workspace ?: Path.of("").toAbsolutePath()
