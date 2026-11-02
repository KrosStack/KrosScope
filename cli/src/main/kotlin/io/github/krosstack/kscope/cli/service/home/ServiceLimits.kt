package io.github.krosstack.kscope.cli.service.home

import java.util.concurrent.TimeUnit

internal data class ServiceLimits(val idleMinutes: Long? = null, val memoryMib: Long? = null) {
    val idleMillis: Long get() = TimeUnit.MINUTES.toMillis(idleMinutes ?: DEFAULT_IDLE_MINUTES)
    val memoryBytes: Long get() = (memoryMib ?: 0L).coerceAtMost(MAXIMUM_MIB) * BYTES_PER_MIB

    val text: String
        get() {
            val idle = (idleMinutes ?: DEFAULT_IDLE_MINUTES).takeIf { it > 0 }?.let { "$it minutes" } ?: "none"
            val memory = memoryMib?.takeIf { it > 0 }?.let { "$it MiB" } ?: "none"
            return "$LINE_PREFIX idle $idle, memory $memory."
        }

    companion object {
        const val LINE_PREFIX = "Limits:"
        const val DEFAULT_IDLE_MINUTES = 30L
        private const val BYTES_PER_MIB = 1_048_576L
        private const val MAXIMUM_MIB = Long.MAX_VALUE / BYTES_PER_MIB
    }
}
