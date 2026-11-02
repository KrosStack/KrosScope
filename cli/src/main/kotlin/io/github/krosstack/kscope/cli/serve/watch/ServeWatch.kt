package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.workspaceState
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class ServeWatch(private val workspace: Path, settings: ServeSettings) : AutoCloseable {
    private val options = settings.watch
    private val service = if (options.stamp == null) KrosScope.open(workspace) else null
    private val source = service?.workspaceState()
    private val stamp = options.stamp?.let { injected -> { injected(workspace) } } ?: checkNotNull(source)::stamp
    private val git = options.git?.invoke(workspace) ?: if (source == null) {
        WatchGitState.open(workspace)
    } else {
        WatchGitState.fromDirectories { source.gitDirectories }
    }
    val state = WatchState(options, settings.clock, stamp, git)
    private val hosted = settings.hosted
    private val stopping = AtomicBoolean()
    private val wake = CountDownLatch(1)
    private val observer = Thread(::observe, "kscope-serve-watch").apply { isDaemon = true }

    fun start() = observer.start()

    override fun close() {
        stopping.set(true)
        wake.countDown()
        try {
            observer.join()
        } finally {
            service?.close()
        }
    }

    private fun observe() {
        while (!stopping.get() && !state.stopped) {
            if (hosted) state.freshness.apply(options.freshness(workspace))
            val interval = state.poll()
            options.onPoll()
            wake.await(interval, TimeUnit.MILLISECONDS)
        }
    }
}
