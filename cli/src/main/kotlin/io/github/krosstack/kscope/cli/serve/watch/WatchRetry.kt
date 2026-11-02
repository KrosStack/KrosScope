package io.github.krosstack.kscope.cli.serve.watch

import java.util.concurrent.TimeUnit

internal class WatchRetry(private val timing: WatchTiming) {
    var failures = 0
        private set
    var nextAttempt = Long.MIN_VALUE
        private set
    var delayMillis = 0L
        private set
    val stopped: Boolean get() = failures >= MAX_FAILURES

    fun fail(now: Long) {
        failures++
        delayMillis = if (delayMillis == 0L) {
            timing.retryMillis
        } else {
            (delayMillis * BACKOFF_MULTIPLIER).coerceAtMost(timing.maximumRetryMillis)
        }
        nextAttempt = now + TimeUnit.MILLISECONDS.toNanos(delayMillis)
    }

    fun reset() {
        failures = 0
        delayMillis = 0
        nextAttempt = Long.MIN_VALUE
    }

    private companion object {
        const val MAX_FAILURES = 5
        const val BACKOFF_MULTIPLIER = 2
    }
}
