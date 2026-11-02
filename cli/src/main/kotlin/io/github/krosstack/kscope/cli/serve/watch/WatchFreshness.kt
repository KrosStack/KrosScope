package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.service.settings.FreshnessReading
import java.util.concurrent.TimeUnit

internal class WatchFreshness(initialQuietMillis: Long) {
    private data class Snapshot(val quietMillis: Long, val watching: Boolean, val invalid: Boolean)

    @Volatile
    private var current = Snapshot(initialQuietMillis, watching = true, invalid = false)

    val quietMillis: Long get() = current.quietMillis
    val watching: Boolean get() = current.watching
    val invalid: Boolean get() = current.invalid

    fun apply(reading: FreshnessReading) {
        val previous = current
        current = if (reading.valid) {
            Snapshot(TimeUnit.SECONDS.toMillis(reading.settings.quietSeconds.toLong()), reading.settings.watch, false)
        } else {
            previous.copy(invalid = true)
        }
    }

    val holdsUpdates: Boolean get() = current.let { it.invalid || !it.watching }
}
