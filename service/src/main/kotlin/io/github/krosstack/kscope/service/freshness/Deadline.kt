package io.github.krosstack.kscope.service.freshness

import java.util.concurrent.TimeUnit

internal class Deadline(limitMillis: Long, private val now: () -> Long = System::nanoTime) {
    private val end = now() + TimeUnit.MILLISECONDS.toNanos(limitMillis)

    fun expired(): Boolean = now() > end

    fun remainingNanos(): Long = end - now()
}
