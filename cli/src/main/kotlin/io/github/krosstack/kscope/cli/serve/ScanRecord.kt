package io.github.krosstack.kscope.cli.serve

import java.time.Instant
import java.util.concurrent.TimeUnit

internal data class ScanRecord(val finishedAt: Instant, val durationMillis: Long, val failure: String?) {
    companion object {
        fun since(startNanos: Long, failure: Throwable?): ScanRecord = ScanRecord(
            Instant.now(),
            TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - startNanos).coerceAtLeast(0)),
            failure?.let { it.message?.takeIf(String::isNotBlank) ?: it.javaClass.simpleName },
        )
    }
}
