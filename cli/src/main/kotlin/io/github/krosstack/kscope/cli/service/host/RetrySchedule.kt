package io.github.krosstack.kscope.cli.service.host

import java.util.concurrent.TimeUnit

internal class RetrySchedule(private val baseMillis: Long, private val maximumMillis: Long) {
    private var retryAt: Long? = null
    private var delayMillis = 0L

    @Synchronized
    fun due(now: Long): Boolean = retryAt?.let { now - it >= 0 } ?: true

    @Synchronized
    fun waitMillis(now: Long): Long? = retryAt?.let { TimeUnit.NANOSECONDS.toMillis(it - now) }

    @Synchronized
    fun failed(now: Long) {
        delayMillis = (if (delayMillis == 0L) baseMillis else delayMillis * DOUBLING).coerceAtMost(maximumMillis)
        retryAt = now + TimeUnit.MILLISECONDS.toNanos(delayMillis)
    }

    @Synchronized
    fun waiting(now: Long) {
        retryAt = now + TimeUnit.MILLISECONDS.toNanos(baseMillis)
    }

    @Synchronized
    fun served() {
        delayMillis = 0L
    }

    @Synchronized
    fun atOnce() {
        retryAt = null
    }

    private companion object {
        const val DOUBLING = 2
    }
}
