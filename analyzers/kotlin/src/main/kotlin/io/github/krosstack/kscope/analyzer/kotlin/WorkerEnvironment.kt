package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.process.HeapSize
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerConcurrency

/**
 * What the environment says about the workers. A variable that is set but not usable counts as not set: the number of
 * workers must be a whole number of at least one, and the heap digits with an optional `k`, `m` or `g`.
 */
public object WorkerEnvironment {
    public const val WORKERS_VARIABLE: String = "KSCOPE_WORKERS"
    public const val HEAP_VARIABLE: String = "KSCOPE_WORKER_HEAP"
    public const val DEFAULT_HEAP_MEBIBYTES: Int = 2048

    public fun workers(environment: (String) -> String?): Int? = environment(WORKERS_VARIABLE)
        ?.trim()
        ?.toIntOrNull()
        ?.takeIf { it >= 1 }
        ?.coerceAtMost(WorkerConcurrency.MAXIMUM)

    public fun heapMebibytes(environment: (String) -> String?): Int? =
        heapOption(environment)?.let(HeapSize::mebibytesOf)

    internal fun heapOption(environment: (String) -> String?): String? =
        environment(HEAP_VARIABLE)?.trim()?.takeIf { HeapSize.bytesOrNull(it) != null }
}
