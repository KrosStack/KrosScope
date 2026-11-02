package io.github.krosstack.kscope.cli.serve.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchStateTest {
    @Test
    fun `unchanged polls across the quiet period want exactly one update`() {
        val clock = WatchTestClock()
        clock.state.scanStarted()
        clock.state.scanCompleted(false)
        clock.change()
        clock.state.poll()
        assertEquals(WatchReason.SETTLING, clock.state.reason())
        clock.at(2)
        clock.state.poll()
        assertFalse(clock.state.beginUpdate())
        clock.at(4)
        clock.state.poll()
        assertTrue(clock.state.beginUpdate())
        assertFalse(clock.state.beginUpdate())
        clock.state.updateCompleted(0, false)
        clock.at(10)
        clock.state.poll()
        assertFalse(clock.state.beginUpdate())
        assertEquals(null, clock.state.reason())
    }

    @Test
    fun `a changing stamp postpones the update and a revert needs none`() {
        val clock = WatchTestClock()
        clock.state.scanStarted()
        clock.state.scanCompleted(false)
        for (second in 0L..10L step 2) {
            clock.at(second)
            clock.change(second.toString())
            clock.state.poll()
            assertFalse(clock.state.beginUpdate())
        }
        clock.at(12)
        clock.state.poll()
        assertFalse(clock.state.beginUpdate())
        clock.at(14)
        clock.state.poll()
        assertTrue(clock.state.beginUpdate())
        clock.state.updateCompleted(0, false)
        clock.change("another")
        clock.at(20)
        clock.state.poll()
        clock.change("10")
        clock.at(24)
        clock.state.poll()
        assertFalse(clock.state.beginUpdate())
    }

    @Test
    fun `git pause restarts quiet time and collects all intervening changes`() {
        val clock = WatchTestClock()
        clock.state.scanStarted()
        clock.state.scanCompleted(false)
        clock.gitPaused = true
        for (second in 0L..20L step 2) {
            clock.at(second)
            clock.change(second.toString())
            clock.state.poll()
            assertFalse(clock.state.beginUpdate())
            assertEquals(WatchReason.GIT, clock.state.reason())
        }
        clock.gitPaused = false
        clock.at(22)
        clock.state.poll()
        assertFalse(clock.state.beginUpdate())
        clock.at(24)
        clock.state.poll()
        assertFalse(clock.state.beginUpdate())
        clock.at(26)
        clock.state.poll()
        assertTrue(clock.state.beginUpdate())
        clock.state.updateCompleted(0, false)
        assertFalse(clock.state.beginUpdate())
    }

    @Test
    fun `trust blocks updates until a completed untrusted scan`() {
        val clock = WatchTestClock()
        clock.state.scanStarted()
        clock.state.scanCompleted(true)
        clock.change()
        clock.state.poll()
        clock.at(10)
        clock.state.poll()
        assertFalse(clock.state.beginUpdate())
        assertEquals(WatchReason.TRUSTED, clock.state.reason())
        clock.state.scanStarted()
        clock.state.scanCompleted(false)
        clock.change("next")
        clock.state.poll()
        clock.at(14)
        clock.state.poll()
        assertTrue(clock.state.beginUpdate())
    }

    @Test
    fun `five seconds separate automatic scans measured from completion`() {
        val clock = WatchTestClock()
        clock.ready()
        clock.at(10)
        clock.state.updateCompleted(0, false)
        clock.change("next")
        clock.state.poll()
        clock.at(14)
        clock.state.poll()
        assertFalse(clock.state.beginUpdate())
        clock.at(15)
        assertTrue(clock.state.beginUpdate())
    }

    @Test
    fun `stamp cost stretches the next interval within the cap`() {
        val clock = WatchTestClock()
        assertEquals(2_000L, clock.state.poll())
        clock.stampCostMillis = 200
        assertEquals(4_000L, clock.state.poll())
        clock.stampCostMillis = 10_000
        assertEquals(60_000L, clock.state.poll())
    }

    @Test
    fun `one observation alone cannot satisfy the quiet period`() {
        val clock = WatchTestClock()
        clock.state.scanStarted()
        clock.state.scanCompleted(false)
        clock.change()
        clock.state.poll()
        clock.at(100)
        assertFalse(clock.state.beginUpdate())
        clock.state.poll()
        assertTrue(clock.state.beginUpdate())
    }

    @Test
    fun `a git marker appearing after the last poll prevents starting`() {
        val clock = WatchTestClock()
        clock.state.scanStarted()
        clock.state.scanCompleted(false)
        clock.change()
        clock.state.poll()
        clock.at(4)
        clock.state.poll()
        clock.gitPaused = true
        assertFalse(clock.state.beginUpdate())
        clock.gitPaused = false
        clock.at(8)
        clock.state.poll()
        assertFalse(clock.state.beginUpdate())
        clock.at(12)
        clock.state.poll()
        assertTrue(clock.state.beginUpdate())
    }
}
