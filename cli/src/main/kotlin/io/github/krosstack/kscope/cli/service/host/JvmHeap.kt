package io.github.krosstack.kscope.cli.service.host

import java.lang.management.ManagementFactory
import java.lang.management.MemoryType

internal object JvmHeap : HeapStatistics {
    override val maximumBytes: Long get() = Runtime.getRuntime().maxMemory()

    override fun reading(): HeapReading {
        val used = ManagementFactory.getMemoryPoolMXBeans()
            .filter { it.type == MemoryType.HEAP }
            .sumOf { it.collectionUsage?.used ?: 0L }
        val collections = ManagementFactory.getGarbageCollectorMXBeans().sumOf { it.collectionCount.coerceAtLeast(0L) }
        return HeapReading(used, collections)
    }
}
