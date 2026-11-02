package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.model.Fingerprint
import java.util.concurrent.TimeUnit

internal class WatchTestClock {
    var nanos = 0L
    var value = Fingerprint("initial")
    var gitPaused = false
    var stampCostMillis = 0L
    var reads = 0
    val state = WatchState(WatchSettings(), { nanos }, ::stamp, { gitPaused })

    fun at(seconds: Long) {
        nanos = TimeUnit.SECONDS.toNanos(seconds)
    }

    fun stamp(): Fingerprint {
        reads++
        nanos += TimeUnit.MILLISECONDS.toNanos(stampCostMillis)
        return value
    }

    fun change(text: String = "changed") {
        value = Fingerprint(text)
    }

    fun ready() {
        state.scanStarted()
        state.scanCompleted(false)
        change()
        state.poll()
        at(4)
        state.poll()
        check(state.beginUpdate())
    }
}
