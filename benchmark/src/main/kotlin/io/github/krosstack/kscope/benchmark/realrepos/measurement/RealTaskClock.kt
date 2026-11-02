package io.github.krosstack.kscope.benchmark.realrepos.measurement

import io.github.krosstack.kscope.benchmark.realrepos.RealTimings
import kotlin.time.TimeSource

internal enum class RealTaskPhase(val label: String) {
    SNAPSHOT("snapshot"),
    PARENT_CHECKOUT("parent-checkout"),
    PARENT_SCAN("parent-scan"),
    LOCALIZATION("localization"),
    CONTEXT("context"),
    CHILD_CHECKOUT("child-checkout"),
    INCREMENTAL_UPDATE("incremental-update"),
    VERIFICATION("verification"),
}

internal class RealTaskClock {
    var timings: RealTimings = RealTimings()
        private set

    fun record(phase: RealTaskPhase, operation: () -> Unit) {
        val start = TimeSource.Monotonic.markNow()
        try {
            operation()
        } finally {
            retain(phase, start.elapsedNow().inWholeMilliseconds)
        }
    }

    private fun retain(phase: RealTaskPhase, elapsed: Long) {
        timings = when (phase) {
            RealTaskPhase.PARENT_SCAN -> timings.copy(fullScanMillis = elapsed)
            RealTaskPhase.LOCALIZATION -> timings.copy(localizationMillis = elapsed)
            RealTaskPhase.CONTEXT -> timings.copy(contextMillis = elapsed)
            RealTaskPhase.INCREMENTAL_UPDATE -> timings.copy(incrementalMillis = elapsed)
            RealTaskPhase.VERIFICATION -> timings.copy(verificationMillis = elapsed)
            RealTaskPhase.SNAPSHOT, RealTaskPhase.PARENT_CHECKOUT, RealTaskPhase.CHILD_CHECKOUT -> timings
        }
    }
}
