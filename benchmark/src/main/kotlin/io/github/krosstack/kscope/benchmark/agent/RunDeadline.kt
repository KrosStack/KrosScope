package io.github.krosstack.kscope.benchmark.agent

import java.time.Duration

internal class RunDeadline private constructor(private val at: Long?) {
    val reached: Boolean get() = at != null && System.nanoTime() - at >= 0

    fun limit(requested: Duration): Duration {
        if (at == null) return requested
        val remaining = Duration.ofNanos((at - System.nanoTime()).coerceAtLeast(MINIMUM_NANOS))
        return if (remaining < requested) remaining else requested
    }

    companion object {
        val NONE = RunDeadline(null)

        fun after(cap: Duration?): RunDeadline = cap?.let { RunDeadline(System.nanoTime() + it.toNanos()) } ?: NONE

        private const val MINIMUM_NANOS = 1_000_000_000L
    }
}
