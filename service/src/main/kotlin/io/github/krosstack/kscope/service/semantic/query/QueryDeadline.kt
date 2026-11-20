package io.github.krosstack.kscope.service.semantic.query

import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal class QueryDeadline(clock: TimeSource = TimeSource.Monotonic) {
    private val started = clock.markNow()

    fun remaining(): Duration = maxOf(10.seconds - started.elapsedNow(), Duration.ZERO)

    fun requireRemaining(): Duration = remaining().also {
        if (it == Duration.ZERO) throw TimeoutException("Semantic query deadline exceeded")
    }

    fun expired(): Boolean = remaining() == Duration.ZERO
}
