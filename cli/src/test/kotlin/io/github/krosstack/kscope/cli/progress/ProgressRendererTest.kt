package io.github.krosstack.kscope.cli.progress

import io.github.krosstack.kscope.service.ProgressEvent
import io.github.krosstack.kscope.service.ProgressStage
import io.github.krosstack.kscope.service.ScanStages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressRendererTest {
    private val clock = ManualClock()
    private val screen = StringBuilder()

    private fun renderer(style: ProgressStyle, columns: Int = 80, timing: ProgressTiming = ProgressTiming()) =
        ProgressRenderer(style, ProgressScreen(columns, false) { screen.append(it) }, clock::nanos, timing)

    private fun ProgressRenderer.stage(stage: ProgressStage, millis: Long, done: Long = 0, total: Long? = null) {
        onEvent(ProgressEvent.Started(stage))
        if (total != null) onEvent(ProgressEvent.Advanced(stage, done, total, "units"))
        clock.advance(millis)
        onEvent(ProgressEvent.Finished(stage))
    }

    @Test
    fun `captured output has one plain line per slow stage and a summary`() {
        val renderer = renderer(ProgressStyle.PLAIN)
        renderer.stage(ScanStages.DISCOVERY, 2_500, 154, 154)
        renderer.stage(ScanStages.SOURCE_SETS, 300)
        renderer.stage(ScanStages.PRODUCTION, 90_000, 68, 68)
        renderer.close(succeeded = true)

        assertEquals(
            "Listing files: 154 units (2.5 s)\nAnalysing sources: 68 units (1 m 30 s)\nDone in 1 m 33 s.\n",
            screen.toString(),
        )
    }

    @Test
    fun `a long stage is reported as still running every ten seconds and not more often`() {
        val renderer = renderer(ProgressStyle.PLAIN)
        renderer.onEvent(ProgressEvent.Started(ScanStages.PRODUCTION))
        renderer.onEvent(ProgressEvent.Advanced(ScanStages.PRODUCTION, 4, 68, "units"))
        listOf(4_000L, 6_000L, 3_000L, 4_000L, 9_000L).forEach {
            clock.advance(it)
            renderer.tick()
        }

        assertEquals(
            "Analysing sources: 4 of 68 units (5%), still running (10 s)\n" +
                "Analysing sources: 4 of 68 units (5%), still running (26 s)\n",
            screen.toString(),
        )
    }

    @Test
    fun `a command that finishes quickly shows nothing at all`() {
        val renderer = renderer(ProgressStyle.PLAIN)
        renderer.stage(ScanStages.DISCOVERY, 400, 3, 3)
        renderer.close(succeeded = true)

        assertEquals("", screen.toString())
    }

    @Test
    fun `forced progress reports every stage however short`() {
        val renderer = renderer(ProgressStyle.PLAIN, timing = ProgressTiming(showAfterMillis = 0))
        renderer.stage(ScanStages.DISCOVERY, 400, 3, 3)
        renderer.close(succeeded = false)

        assertEquals("Listing files: 3 units (400 ms)\nStopped after 400 ms.\n", screen.toString())
    }

    @Test
    fun `the live line appears after a second and is redrawn at most ten times a second`() {
        val renderer = renderer(ProgressStyle.LIVE)
        renderer.onEvent(ProgressEvent.Started(ScanStages.PRODUCTION))
        clock.advance(900)
        renderer.onEvent(ProgressEvent.Advanced(ScanStages.PRODUCTION, 0, 1_000, "units"))
        assertEquals("", screen.toString(), "nothing is shown during the first second")

        repeat(1_000) { count ->
            clock.advance(1)
            renderer.onEvent(ProgressEvent.Advanced(ScanStages.PRODUCTION, count + 1L, 1_000, "units"))
        }

        assertEquals(10, screen.count { it == '\r' }, "draws in the second after the line appeared")
        assertTrue(screen.contains("Analysing sources: 1,000 units - 1.9 s"), screen.toString())
    }

    @Test
    fun `the live line shows stage counts and elapsed time and is cleared when the command ends`() {
        val renderer = renderer(ProgressStyle.LIVE)
        renderer.onEvent(ProgressEvent.Started(ScanStages.LINKING))
        clock.advance(2_000)
        renderer.onEvent(ProgressEvent.Advanced(ScanStages.LINKING, 3, 10, "linkers"))
        renderer.close(succeeded = true)

        val line = "Linking references: 3 of 10 linkers (30%) - 2.0 s"
        assertEquals("\r$line\r${" ".repeat(line.length)}\rDone in 2.0 s.\n", screen.toString())
    }

    @Test
    fun `a shorter line wipes what a longer one left and a long line is cut to the width`() {
        val renderer = renderer(ProgressStyle.LIVE, columns = 24)
        renderer.onEvent(ProgressEvent.Started(ScanStages.PRODUCTION))
        clock.advance(2_000)
        renderer.tick()
        renderer.onEvent(ProgressEvent.Started(ScanStages.DISCOVERY))
        clock.advance(200)
        renderer.tick()

        assertEquals("\rAnalysing sources - ...\rListing files - 2.0 s  \rListing files - 2.2 s", screen.toString())
    }

    @Test
    fun `an off renderer writes nothing`() {
        val renderer = renderer(ProgressStyle.OFF)
        renderer.stage(ScanStages.PRODUCTION, 60_000, 1, 2)
        renderer.tick()
        renderer.close(succeeded = true)

        assertEquals("", screen.toString())
    }

    @Test
    fun `a stage that is verified is named as such`() {
        val renderer = renderer(ProgressStyle.PLAIN)
        renderer.stage(ProgressStage(ScanStages.VERIFY_PREFIX + ScanStages.PRODUCTION.id), 3_000, 68, 68)

        assertEquals("Verification: analysing sources: 68 units (3.0 s)\n", screen.toString())
    }
}

private class ManualClock {
    private var millis = 0L

    fun advance(by: Long) {
        millis += by
    }

    fun nanos(): Long = millis * NANOS_PER_MILLI

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
