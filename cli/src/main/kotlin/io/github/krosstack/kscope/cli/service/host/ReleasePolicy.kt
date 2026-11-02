package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.service.home.ServiceLimits
import java.util.concurrent.TimeUnit

internal class ReleasePolicy(private val heap: HeapStatistics, private val clock: () -> Long) {
    private var collectionsAtLastRelease = NO_COLLECTION

    @Synchronized
    fun look(limits: ServiceLimits, candidates: List<ReleaseCandidate>) {
        val loaded = candidates.filter { it.loaded }
        val idle = loaded.filter { idleBeyond(it, limits.idleMillis) }
        idle.forEach { it.release() }
        if (idle.isEmpty()) releaseForMemory(limits, loaded)
    }

    private fun idleBeyond(candidate: ReleaseCandidate, limitMillis: Long): Boolean =
        limitMillis > 0 && clock() - candidate.lastRequestAt >= TimeUnit.MILLISECONDS.toNanos(limitMillis)

    private fun releaseForMemory(limits: ServiceLimits, loaded: List<ReleaseCandidate>) {
        val oldest = loaded.minByOrNull { it.lastRequestAt } ?: return
        val reading = heap.reading()
        val exceeded = reading.usedAfterCollection > ceilingBytes(limits)
        if (exceeded && reading.collections > collectionsAtLastRelease) {
            oldest.release()
            collectionsAtLastRelease = reading.collections
        }
    }

    private fun ceilingBytes(limits: ServiceLimits): Long {
        val configured = limits.memoryBytes
        val smaller = if (configured > 0) minOf(configured, heap.maximumBytes) else heap.maximumBytes
        return smaller / CEILING_DENOMINATOR * CEILING_NUMERATOR
    }

    private companion object {
        const val NO_COLLECTION = -1L
        const val CEILING_NUMERATOR = 4
        const val CEILING_DENOMINATOR = 5
    }
}
