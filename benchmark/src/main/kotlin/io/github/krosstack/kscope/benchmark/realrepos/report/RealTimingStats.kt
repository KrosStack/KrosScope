package io.github.krosstack.kscope.benchmark.realrepos.report

import io.github.krosstack.kscope.benchmark.realrepos.RealTimings
import io.github.krosstack.kscope.benchmark.stats.BootstrapPercentiles

internal object RealTimingStats {
    fun of(records: List<RealNumericRecord>): Map<RealTimingKind, RealTimingSummary> =
        RealTimingKind.entries.associateWith { kind -> summary(records.mapNotNull { value(it.timings, kind) }) }

    private fun summary(values: List<Long>): RealTimingSummary {
        val measured = values.filter { it >= 0 }.map { it.toDouble() }.sorted().toDoubleArray()
        return if (measured.isEmpty()) {
            RealTimingSummary(0, null, null, null)
        } else {
            RealTimingSummary(
                measured.size,
                measured.average(),
                BootstrapPercentiles.value(measured, MEDIAN),
                BootstrapPercentiles.value(measured, P95),
            )
        }
    }

    private fun value(timings: RealTimings, kind: RealTimingKind): Long? = when (kind) {
        RealTimingKind.FULL_SCAN -> timings.fullScanMillis
        RealTimingKind.INCREMENTAL -> timings.incrementalMillis
        RealTimingKind.LOCALIZATION -> timings.localizationMillis
        RealTimingKind.CONTEXT -> timings.contextMillis.takeUnless { timings.contextResumed }
        RealTimingKind.VERIFICATION -> timings.verificationMillis
    }

    private const val MEDIAN = 0.5
    private const val P95 = 0.95
}
