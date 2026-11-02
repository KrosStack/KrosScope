package io.github.krosstack.kscope.cli.serve.map

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal sealed interface MapObservation {
    data class Changed(val sequence: Long, val state: MapState) : MapObservation

    data object Quiet : MapObservation

    data object Closed : MapObservation
}

/** The state that viewers watch. Each change gets a sequence number, and a viewer waits for the next one. */
internal class MapStates : AutoCloseable {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private var state = MapState(0, false, null, false, false, null)
    private var sequence = 1L
    private var closed = false
    private var watching = 0

    val viewers: Int get() = lock.withLock { watching }

    fun update(change: (MapState) -> MapState) = lock.withLock {
        val next = change(state)
        if (next != state) {
            state = next
            sequence++
            changed.signalAll()
        }
    }

    fun joined() = lock.withLock { watching++ }

    fun left() = lock.withLock { watching-- }

    /** Waits until the sequence is not [seen], the wait ends or the states are closed. */
    fun await(seen: Long, timeoutMillis: Long): MapObservation = lock.withLock {
        var remaining = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!closed && sequence == seen && remaining > 0) remaining = changed.awaitNanos(remaining)
        when {
            closed -> MapObservation.Closed
            sequence != seen -> MapObservation.Changed(sequence, state)
            else -> MapObservation.Quiet
        }
    }

    override fun close() = lock.withLock {
        closed = true
        changed.signalAll()
    }
}
