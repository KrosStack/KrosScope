package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.serve.ServeServer
import io.github.krosstack.kscope.cli.serve.ServeSettings
import java.nio.file.Path

internal data class ServiceSettings(
    val serve: ServeSettings = ServeSettings(),
    val scanRunner: ScanRunner = ScanRunner.DEFAULT,
    val retryMillis: Long = RETRY_MILLIS,
    val clock: () -> Long = System::nanoTime,
    val onChange: (Path, WorkspaceState) -> Unit = { _, _ -> },
    val opening: (Path, ServeSettings, ScanRunner) -> ServeServer = { path, settings, runner ->
        ServeServer.open(path, settings, runner)
    },
    val serving: (ServeServer) -> Unit = { it.run() },
    val maximumRetryMillis: Long = MAXIMUM_RETRY_MILLIS,
    val heap: HeapStatistics = JvmHeap,
    val lookMillis: Long = LOOK_MILLIS,
) {
    companion object {
        const val RETRY_MILLIS = 30_000L
        const val MAXIMUM_RETRY_MILLIS = 600_000L
        const val LOOK_MILLIS = 10_000L
    }
}
