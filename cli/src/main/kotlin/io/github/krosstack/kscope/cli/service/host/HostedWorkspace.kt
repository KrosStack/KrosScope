package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.serve.ServeServer
import java.nio.file.Path

internal class HostedWorkspace(val path: Path, private val settings: ServiceSettings, private val wake: () -> Unit) {
    @Volatile
    var state: WorkspaceState = WorkspaceState.Starting
        private set

    @Volatile
    private var server: ServeServer? = null
    private var thread: Thread? = null
    private val retry = RetrySchedule(settings.retryMillis, settings.maximumRetryMillis)
    private var stopped = false

    fun due(now: Long): Boolean = synchronized(this) { !stopped && server == null && retry.due(now) }

    fun waitMillis(now: Long): Long? = synchronized(this) {
        retry.waitMillis(now)?.takeIf { !stopped && server == null }
    }

    fun open() {
        synchronized(this) {
            if (!stopped) attempt()
        }
    }

    fun retryAtOnce() {
        if (state !is WorkspaceState.Served) retry.atOnce()
    }

    fun candidate(): ReleaseCandidate? =
        server?.let { ReleaseCandidate(it.loaded, it.lastRequestAt, it.releases::request) }

    fun stop() {
        val serving = synchronized(this) {
            stopped = true
            server?.requestStop()
            thread
        }
        serving?.join()
    }

    // Opening and serving run at the edge of long-lived threads: an Error ends this workspace, never the thread that
    // opens the others or the service.
    private fun attempt() {
        val serve = settings.serve.copy(
            onLoadChange = { _ -> (state as? WorkspaceState.Served)?.let { settings.onChange(path, it) } },
        )
        runCatching { start(settings.opening(path, serve, settings.scanRunner)) }.onFailure {
            val next = WorkspaceFailures.stateOf(path, it)
            if (next == WorkspaceState.Waiting) retry.waiting(settings.clock()) else retry.failed(settings.clock())
            change(next)
        }
    }

    private fun start(opened: ServeServer) {
        val serving = Thread({ serve(opened) }, "kscope-service-${path.fileName}").apply { isDaemon = true }
        runCatching { serving.start() }.onFailure {
            opened.close()
            throw it
        }
        server = opened
        thread = serving
        retry.served()
        change(WorkspaceState.Served(opened))
    }

    private fun serve(opened: ServeServer) {
        val failure = runCatching { settings.serving(opened) }.exceptionOrNull()
        synchronized(this) {
            server = null
            thread = null
            if (!stopped) endUnexpectedly(failure)
        }
    }

    private fun endUnexpectedly(failure: Throwable?) {
        retry.failed(settings.clock())
        change(WorkspaceState.NotServed(failure?.let(WorkspaceFailures::describe) ?: "the server ended"))
        wake()
    }

    private fun change(next: WorkspaceState) {
        if (next == state) return
        state = next
        settings.onChange(path, next)
    }
}
