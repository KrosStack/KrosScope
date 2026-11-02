package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.analyzer.kotlin.WorkerEnvironment
import io.github.krosstack.kscope.analyzer.kotlin.WorkerSettings
import java.lang.management.ManagementFactory

/**
 * How many worker JVMs may analyse at the same time. The environment variable `KSCOPE_WORKERS` sets the number, as it
 * is. Else the number of the settings of the workspace applies, limited to the workers that the available memory
 * holds. Else a worker is allowed for every two processors and for every heap of the worker plus [NATIVE_OVERHEAD]
 * that the available memory holds beyond [CLIENT_RESERVE] for the client, and never more than [DEFAULT_MAXIMUM].
 */
internal class WorkerConcurrency(val workers: Int) {
    init {
        require(workers >= 1) { "At least one worker is needed" }
    }

    companion object {
        const val VARIABLE = WorkerEnvironment.WORKERS_VARIABLE
        const val DEFAULT_MAXIMUM = 8
        const val MAXIMUM = 64
        const val NATIVE_OVERHEAD = 1L shl 30
        const val CLIENT_RESERVE = 3L shl 30

        private const val PROCESSORS_PER_WORKER = 2
        private const val UNKNOWN_MEMORY = 8L shl 30

        fun fromEnvironment(settings: WorkerSettings = WorkerSettings.NONE): WorkerConcurrency = resolve(
            environment = System::getenv,
            processors = Runtime.getRuntime().availableProcessors(),
            availableMemory = ::availableMemoryBytes,
            settings = settings,
        )

        fun resolve(
            environment: (String) -> String?,
            processors: Int,
            availableMemory: () -> Long,
            settings: WorkerSettings = WorkerSettings.NONE,
        ): WorkerConcurrency {
            val requested = WorkerEnvironment.workers(environment)
            return WorkerConcurrency(requested ?: derived(environment, processors, availableMemory, settings))
        }

        private fun derived(
            environment: (String) -> String?,
            processors: Int,
            availableMemory: () -> Long,
            settings: WorkerSettings,
        ): Int {
            val heap = WorkerLocator.heapSetting(environment, settings.heapMebibytes)
            val perWorker = HeapSize.bytesOf(heap) + NATIVE_OVERHEAD
            val byMemory = ((availableMemory() - CLIENT_RESERVE) / perWorker).coerceIn(1L, MAXIMUM.toLong()).toInt()
            val configured = settings.workers
            return if (configured != null) {
                minOf(configured, byMemory)
            } else {
                minOf(maxOf(1, processors / PROCESSORS_PER_WORKER), byMemory, DEFAULT_MAXIMUM)
            }
        }

        private fun availableMemoryBytes(): Long =
            (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)
                ?.freeMemorySize ?: UNKNOWN_MEMORY
    }
}
