package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.service.settings.FreshnessReading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchFreshnessTest {
    private fun behind(): WatchTestClock {
        val clock = WatchTestClock()
        clock.state.scanStarted()
        clock.state.scanCompleted(false)
        clock.change()
        clock.state.poll()
        return clock
    }

    @Test
    fun `a longer quiet period postpones the update and a shorter one brings it forward`() {
        val clock = behind()
        clock.state.freshness.apply(quietFreshness(10))
        clock.at(4)
        clock.state.poll()
        assertFalse(clock.state.beginUpdate())
        clock.at(10)
        clock.state.poll()
        assertTrue(clock.state.beginUpdate())
        clock.state.updateCompleted(0, false)

        clock.change("again")
        clock.at(20)
        clock.state.freshness.apply(quietFreshness(1))
        clock.state.poll()
        clock.at(22)
        clock.state.poll()
        assertTrue(clock.state.beginUpdate())
    }

    @Test
    fun `watching turned off holds the updates and says why until it is turned on again`() {
        val clock = behind()
        clock.state.freshness.apply(unwatchedFreshness())
        clock.at(10)
        clock.state.poll()

        assertFalse(clock.state.updateWanted)
        assertFalse(clock.state.beginUpdate())
        assertEquals(WatchReason.OFF, clock.state.reason())
        assertEquals("Watcher: behind; ${WatchReason.OFF.text}.", clock.state.status())

        clock.state.freshness.apply(FreshnessReading())
        assertTrue(clock.state.updateWanted)
        assertTrue(clock.state.beginUpdate())
    }

    @Test
    fun `an invalid file holds the updates with its own reason and keeps the last good values`() {
        val clock = behind()
        clock.state.freshness.apply(quietFreshness(10))
        clock.state.freshness.apply(invalidFreshness())
        clock.at(20)
        clock.state.poll()

        assertFalse(clock.state.beginUpdate())
        assertEquals(WatchReason.SETTINGS, clock.state.reason())

        clock.state.freshness.apply(quietFreshness(10))
        clock.at(21)
        clock.state.poll()
        assertEquals(WatchReason.SETTLING, clock.state.reason())
        clock.at(31)
        clock.state.poll()
        assertTrue(clock.state.beginUpdate())
    }

    @Test
    fun `an index that is current has no reason whatever the file says`() {
        val clock = WatchTestClock()
        clock.state.scanStarted()
        clock.state.scanCompleted(false)
        clock.state.freshness.apply(invalidFreshness())

        assertEquals(null, clock.state.reason())
        clock.state.freshness.apply(unwatchedFreshness())
        assertEquals(null, clock.state.reason())
    }
}
