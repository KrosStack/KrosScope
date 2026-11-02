package io.github.krosstack.kscope.cli.progress

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Starts a task that runs at a fixed pace until the returned handle is closed. */
internal fun interface TickerFactory {
    fun start(periodMillis: Long, task: Runnable): AutoCloseable

    companion object {
        val NONE = TickerFactory { _, _ -> AutoCloseable { } }

        val DAEMON_THREAD = TickerFactory { periodMillis, task ->
            val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "kscope-progress").apply { isDaemon = true }
            }
            executor.scheduleWithFixedDelay(task, periodMillis, periodMillis, TimeUnit.MILLISECONDS)
            AutoCloseable { executor.shutdownNow() }
        }
    }
}
