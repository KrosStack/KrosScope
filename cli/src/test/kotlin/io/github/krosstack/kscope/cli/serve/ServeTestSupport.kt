package io.github.krosstack.kscope.cli.serve

import com.github.ajalt.clikt.testing.test
import io.github.krosstack.kscope.cli.CommandExecution
import io.github.krosstack.kscope.cli.KscopeCommand
import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.parseWithExitCodes
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal fun <T> withTestServer(
    root: Path,
    settings: ServeSettings = ServeSettings(),
    scanRunner: ScanRunner = ScanRunner.DEFAULT,
    onQueued: () -> Unit = {},
    block: (ServeServer) -> T,
): T {
    val server = ServeServer.open(root, settings, scanRunner, onQueued)
    val executor = Executors.newSingleThreadExecutor()
    val running = executor.submit { server.run() }
    try {
        return block(server)
    } finally {
        server.requestStop()
        running.get(30, TimeUnit.SECONDS)
        executor.shutdownNow()
        server.close()
    }
}

internal fun runAt(directory: Path, args: List<String>) =
    KscopeCommand(execution = CommandExecution(directory)).let { command ->
        command.test(args) { command.parseWithExitCodes(it) }
    }

internal fun KscopeCommand.testWithVariables(args: List<String>, variables: Map<String, String>) =
    test(args, envvars = variables) { parseWithExitCodes(it) }
