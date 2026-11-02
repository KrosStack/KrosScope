package io.github.krosstack.kscope.benchmark.realrepos.measurement

import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RealTaskClockTest {
    @Test
    fun `an attempted scan retains elapsed time when it throws and untouched phases stay absent`() {
        val clock = RealTaskClock()
        assertFailsWith<IOException> {
            clock.record(RealTaskPhase.PARENT_SCAN) { throw IOException("Synthetic scan failure") }
        }
        assertTrue(assertNotNull(clock.timings.fullScanMillis) >= 0)
        assertNull(clock.timings.localizationMillis)
        assertNull(clock.timings.contextMillis)
        assertNull(clock.timings.incrementalMillis)
        assertNull(clock.timings.verificationMillis)
    }

    @Test
    fun `failed context timing preserves earlier scan and localization timings`() {
        val clock = RealTaskClock()
        var operations = 0
        clock.record(RealTaskPhase.PARENT_SCAN) { operations++ }
        clock.record(RealTaskPhase.LOCALIZATION) { operations++ }
        val previous = clock.timings
        assertFailsWith<IllegalStateException> {
            clock.record(RealTaskPhase.CONTEXT) { error("Synthetic context failure") }
        }
        assertEquals(previous.fullScanMillis, clock.timings.fullScanMillis)
        assertEquals(previous.localizationMillis, clock.timings.localizationMillis)
        assertEquals(2, operations)
        assertTrue(assertNotNull(clock.timings.contextMillis) >= 0)
        assertNull(clock.timings.incrementalMillis)
        assertNull(clock.timings.verificationMillis)
    }
}
