package io.github.krosstack.kscope.cli.serve.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchDeepeningTest {
    @Test
    fun `an index built from syntax is deepened once the files have been quiet, without a change`() {
        val clock = WatchTestClock()
        clock.state.scanStarted()
        clock.state.scanCompleted(false)
        clock.state.shallow = true
        assertEquals(WatchReason.DEEPENING, clock.state.reason())
        clock.state.poll()
        clock.at(2)
        clock.state.poll()
        assertFalse(clock.state.beginUpdate())
        clock.at(4)
        clock.state.poll()
        assertTrue(clock.state.beginUpdate())
        clock.state.updateCompleted(0, false)
        clock.state.shallow = false
        clock.at(10)
        clock.state.poll()
        assertFalse(clock.state.beginUpdate())
        assertEquals(null, clock.state.reason())
    }

    @Test
    fun `an index that is deep enough waits for a change as before`() {
        val clock = WatchTestClock()
        clock.state.scanStarted()
        clock.state.scanCompleted(false)
        clock.state.shallow = false
        clock.at(6)
        clock.state.poll()
        assertFalse(clock.state.beginUpdate())
        assertEquals(null, clock.state.reason())
    }

    @Test
    fun `a failed deepening is retried later and the reason stays`() {
        val clock = WatchTestClock()
        clock.state.scanStarted()
        clock.state.scanCompleted(false)
        clock.state.shallow = true
        clock.state.poll()
        clock.at(4)
        clock.state.poll()
        assertTrue(clock.state.beginUpdate())
        clock.state.updateCompleted(1, false)
        assertEquals(WatchReason.RETRY, clock.state.reason())
    }

    @Test
    fun `a scan that succeeds but leaves the index shallow backs off and stops after five attempts`() {
        val clock = shallowAfterScan()

        val starts = clock.attempts(until = 2_000)

        assertEquals(listOf(4L, 14L, 34L, 74L, 154L), starts)
        assertEquals(WatchReason.DEEPENING_STOPPED, clock.state.reason())
        assertFalse(clock.state.updateWanted)
    }

    @Test
    fun `an attempt that left the index shallow says so and is retried`() {
        val clock = shallowAfterScan()

        clock.attempts(until = 4)

        assertEquals(WatchReason.DEEPENING_RETRY, clock.state.reason())
        assertEquals("Watcher: behind; ${WatchReason.DEEPENING_RETRY.text}.", clock.state.status())
    }

    @Test
    fun `a change of the files starts the deepening over after it gave up`() {
        val clock = shallowAfterScan()
        clock.attempts(until = 2_000)

        clock.change()
        clock.at(2_002)
        clock.state.poll()
        clock.at(2_006)
        clock.state.poll()

        assertTrue(clock.state.beginUpdate())
    }

    @Test
    fun `an index that becomes shallow again, as when trust is granted, starts the deepening over`() {
        val clock = shallowAfterScan()
        clock.attempts(until = 2_000)

        clock.state.shallow = false
        clock.state.shallow = true
        clock.at(2_002)
        clock.state.poll()
        clock.at(2_006)
        clock.state.poll()

        assertEquals(WatchReason.DEEPENING, clock.state.reason())
        assertTrue(clock.state.beginUpdate())
    }

    private fun shallowAfterScan(): WatchTestClock = WatchTestClock().apply {
        state.scanStarted()
        state.scanCompleted(false)
        state.shallow = true
    }

    private fun WatchTestClock.attempts(until: Long): List<Long> {
        val starts = mutableListOf<Long>()
        for (second in 0L..until step 2) {
            at(second)
            state.poll()
            if (state.beginUpdate()) {
                starts += second
                state.updateCompleted(0, false)
            }
        }
        return starts
    }
}
