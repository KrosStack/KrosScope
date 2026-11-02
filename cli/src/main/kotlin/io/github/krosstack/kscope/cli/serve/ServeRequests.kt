package io.github.krosstack.kscope.cli.serve

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.mordant.terminal.Terminal
import io.github.krosstack.kscope.cli.CliEnvironment
import io.github.krosstack.kscope.cli.CommandExecution
import io.github.krosstack.kscope.cli.EncodingReport
import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.cli.KscopeCommand
import io.github.krosstack.kscope.cli.RequestTerminal
import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.guarded
import io.github.krosstack.kscope.cli.parseWithExitCodes
import io.github.krosstack.kscope.cli.progress.ProgressScreen
import io.github.krosstack.kscope.cli.progress.TickerFactory
import io.github.krosstack.kscope.cli.serve.map.MapHost
import io.github.krosstack.kscope.cli.serve.watch.WatchSettings
import io.github.krosstack.kscope.cli.serve.watch.WatchState
import java.io.Writer
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

internal class ServeRequests(
    private val workspace: Path,
    private val onRequest: (ServeRequest) -> Unit,
    scanRunner: ScanRunner,
    private val onQueued: () -> Unit,
    onLoadChange: (Boolean) -> Unit = {},
) : AutoCloseable {
    val service = ServeServices(
        workspace,
        scanRunner,
        onLoadChange,
        beforeScan = {
            watch?.scanStarted()
            map?.scanStarted()
        },
        afterScan = { map?.scanFinished() },
    )
    private val requests = AtomicInteger()
    val count: Int get() = requests.get()
    val loaded: Boolean get() = service.loaded
    val indexed: Boolean get() = service.indexed
    val lastScan: ScanRecord? get() = service.lastScan
    val indexLevel: String? get() = service.indexLevel
    var watch: WatchState? = null
    var map: MapHost? = null
    var watchSettings = WatchSettings()

    fun warm() = service.warm()

    fun release() = service.release()

    fun queued() = onQueued()

    fun execute(request: ServeRequest, output: ServeOutput): Int {
        requests.incrementAndGet()
        onRequest(request)
        val scans = service.completedScans
        val attempts = service.attemptedScans
        val queries = service.queries
        val code = runCommand(request, output)
        if (service.attemptedScans != attempts) {
            watch?.scanCompleted(service.requiresTrust(watchSettings), service.completedScans != scans)
            watch?.shallow = service.isShallow(watchSettings)
        }
        if (service.queries != queries) {
            watch?.reason()?.let { output.write("Index is behind: ${it.text}.\n", true) }
        }
        return code
    }

    fun update(): Int = Writer.nullWriter().buffered().use { sink ->
        val request = ServeRequest(
            arguments = listOf("scan", "--quiet", "--progress", "never") + service.automaticLevelArguments(),
            directory = workspace.toString(),
        )
        runCommand(request, ServeOutput(sink))
    }

    fun refreshTrust(): Boolean {
        service.refresh()
        return service.requiresTrust(watchSettings)
    }

    fun refreshDepth(): Boolean {
        service.refresh()
        return service.isShallow(watchSettings)
    }

    private fun runCommand(request: ServeRequest, output: ServeOutput): Int {
        val command = command(request, output)
        return runCatching {
            command.guarded { service.refresh() }
            if (watch != null) {
                watch?.setTrusted(service.requiresTrust(watchSettings))
                watch?.shallow = service.isShallow(watchSettings)
            }
            command.parseWithExitCodes(request.arguments)
            0
        }.getOrElse { failure ->
            failureCode(failure, command, output)
        }
    }

    private fun failureCode(failure: Throwable, command: KscopeCommand, output: ServeOutput): Int = when (failure) {
        is ProgramResult -> failure.statusCode

        is CliktError -> {
            command.echoFormattedHelp(failure)
            failure.statusCode
        }

        else -> {
            output.write("error: ${failure.message.orEmpty()}\n", true)
            ExitCodes.FAILURE
        }
    }

    override fun close() = service.close()

    private fun command(request: ServeRequest, output: ServeOutput): KscopeCommand {
        val terminal = Terminal(
            width = request.rendering.width,
            terminalInterface = RequestTerminal(request.rendering, output),
        )
        val environment = CliEnvironment(
            ProgressScreen(request.rendering.width, request.rendering.stderrIsConsole) { output.write(it, true) },
            System::nanoTime,
            TickerFactory.DAEMON_THREAD,
            EncodingReport.DETACHED,
            terminal,
        )
        return KscopeCommand(environment, CommandExecution(Path.of(request.directory), service, workspace)).apply {
            configureContext {
                readEnvvar = { name -> if (name == "NO_COLOR" && request.rendering.noColor) "1" else null }
            }
        }
    }
}
