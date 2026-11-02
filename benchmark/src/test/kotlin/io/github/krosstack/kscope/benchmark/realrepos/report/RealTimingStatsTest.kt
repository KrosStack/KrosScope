package io.github.krosstack.kscope.benchmark.realrepos.report

import kotlin.test.Test
import kotlin.test.assertEquals

class RealTimingStatsTest {
    @Test
    fun `a context timing that includes loading a checkpoint is left out of the context statistics`() {
        val measurements = RealReportFixture.measurements()
        val first = measurements.first()
        val resumed = first.copy(timings = first.timings.copy(contextMillis = 900, contextResumed = true))
        val records = (listOf(resumed) + measurements.drop(1)).map { RealReportProjection.of(it, it.task) }

        val timings = RealTimingStats.of(records)

        assertEquals(measurements.size - 1, timings.getValue(RealTimingKind.CONTEXT).samples)
        assertEquals(20.0, timings.getValue(RealTimingKind.CONTEXT).meanMillis)
        assertEquals(measurements.size, timings.getValue(RealTimingKind.FULL_SCAN).samples)
    }
}
