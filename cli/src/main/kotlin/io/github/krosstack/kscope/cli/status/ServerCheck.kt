package io.github.krosstack.kscope.cli.status

import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.service.status.ServerState
import io.github.krosstack.kscope.service.status.ServerStatus
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

internal class ServerCheck(private val processes: RunningProcesses = RunningProcesses.SYSTEM) {
    fun of(workspace: Path): ServerStatus {
        val metadata = ServeMetadata.read(workspace) ?: return NONE
        val state = stateOf(metadata)
        return ServerStatus(
            state = state,
            pid = metadata.pid,
            version = metadata.toolVersion,
            startedAt = metadata.processStart ?: metadata.startTime,
            stale = state == ServerState.NOT_RUNNING,
        )
    }

    private fun stateOf(metadata: ServeMetadata): ServerState {
        val process = metadata.pid.takeIf { it > 0 }?.let(processes::find)?.takeIf { it.alive }
        return if (process == null) ServerState.NOT_RUNNING else compared(metadata, process)
    }

    private fun compared(metadata: ServeMetadata, process: ProcessFacts): ServerState {
        val recorded = metadata.processStart?.let(::instantOrNull)
        val actual = process.startedAt
        return when {
            recorded == null || actual == null -> ServerState.UNKNOWN
            Duration.between(recorded, actual).abs() <= TOLERANCE -> ServerState.RUNNING
            else -> ServerState.NOT_RUNNING
        }
    }

    private fun instantOrNull(text: String): Instant? = try {
        Instant.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }

    private companion object {
        val TOLERANCE: Duration = Duration.ofSeconds(1)
        val NONE = ServerStatus(ServerState.NOT_RUNNING, null, null, null, stale = false)
    }
}
