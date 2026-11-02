package io.github.krosstack.kscope.cli.serve.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchFailureTest {
    @Test
    fun `failures back off and stop observing after five attempts`() {
        val clock = WatchTestClock()
        clock.ready()
        var seconds = 4L
        for (wait in listOf(10L, 20L, 40L, 80L)) {
            clock.state.updateCompleted(1, false)
            assertEquals(WatchReason.RETRY, clock.state.reason())
            clock.at(seconds + wait - 1)
            clock.state.poll()
            assertFalse(clock.state.beginUpdate())
            seconds += wait
            clock.at(seconds)
            clock.state.poll()
            assertTrue(clock.state.beginUpdate())
        }
        clock.state.updateCompleted(1, false)
        assertEquals(WatchReason.STOPPED, clock.state.reason())
        val reads = clock.reads
        clock.at(1_000)
        repeat(10) {
            clock.state.poll()
            assertFalse(clock.state.beginUpdate())
        }
        assertEquals(reads, clock.reads)
    }

    @Test
    fun `a success resets consecutive failures and the retry wait`() {
        val clock = WatchTestClock()
        clock.ready()
        clock.state.updateCompleted(1, false)
        clock.at(14)
        clock.state.poll()
        assertTrue(clock.state.beginUpdate())
        clock.state.updateCompleted(0, false)
        clock.change("next")
        clock.at(20)
        clock.state.poll()
        clock.at(24)
        clock.state.poll()
        assertTrue(clock.state.beginUpdate())
        clock.state.updateCompleted(1, false)
        clock.at(33)
        assertFalse(clock.state.beginUpdate())
        clock.at(34)
        assertTrue(clock.state.beginUpdate())
    }

    @Test
    fun `retry durations are capped at five minutes`() {
        val retry = WatchRetry(WatchTiming())
        val waits = (1..6).map {
            retry.fail(0)
            retry.delayMillis
        }
        assertEquals(listOf(10_000L, 20_000L, 40_000L, 80_000L, 160_000L, 300_000L), waits)
    }

    @Test
    fun `stamp exceptions also back off and eventually stop`() {
        var reads = 0
        var nanos = 0L
        val state = WatchState(WatchSettings(), { nanos }, {
            reads++
            error("cannot list")
        }, { false })
        for (second in listOf(0L, 10L, 30L, 70L, 150L)) {
            nanos = java.util.concurrent.TimeUnit.SECONDS.toNanos(second)
            state.poll()
            assertFalse(state.beginUpdate())
        }
        assertEquals(5, reads)
        assertEquals(WatchReason.STOPPED, state.reason())
        state.poll()
        assertEquals(5, reads)
    }

    @Test
    fun `a requested scan after giving up records its stamp without restarting polling`() {
        val clock = WatchTestClock()
        clock.ready()
        repeat(5) { clock.state.updateCompleted(1, false) }
        clock.state.scanStarted()
        clock.state.scanCompleted(false)
        assertEquals(null, clock.state.reason())
        assertTrue(clock.state.status().contains("stopped after failures"))
        val reads = clock.reads
        clock.at(1_000)
        repeat(10) { clock.state.poll() }
        assertEquals(reads, clock.reads)
        assertFalse(clock.state.beginUpdate())
    }

    @Test
    fun `a failed slow stamp still stretches polling within the cap`() {
        var nanos = 0L
        var reads = 0
        val state = WatchState(WatchSettings(), { nanos }, {
            reads++
            nanos += java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
            error("stamp failed")
        }, { false })
        assertEquals(60_000L, state.poll())
        assertEquals(60_000L, state.poll())
        assertEquals(1, reads)
        assertFalse(state.beginUpdate())
    }

    @Test
    fun `an unavailable git view is a bounded failure and prevents automatic scans`() {
        val clock = WatchTestClock()
        val state = WatchState(WatchSettings(), { clock.nanos }, clock::stamp, { error("Git is unavailable") })
        for (second in listOf(0L, 10L, 30L, 70L, 150L)) {
            clock.at(second)
            state.poll()
            assertFalse(state.beginUpdate())
        }
        assertTrue(state.stopped)
        assertEquals(WatchReason.STOPPED, state.reason())
    }
}
