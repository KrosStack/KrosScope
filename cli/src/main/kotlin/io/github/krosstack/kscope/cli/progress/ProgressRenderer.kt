package io.github.krosstack.kscope.cli.progress

import io.github.krosstack.kscope.service.ProgressEvent
import io.github.krosstack.kscope.service.ProgressListener
import java.util.concurrent.TimeUnit

/**
 * When progress becomes visible and how often it changes: nothing is shown before [showAfterMillis] of the command
 * have passed, the live line is redrawn at most every [redrawMillis], and a stage that runs on for [heartbeatMillis]
 * is reported again as still running.
 */
internal class ProgressTiming(
    val showAfterMillis: Long = DEFAULT_SHOW_AFTER_MILLIS,
    val redrawMillis: Long = DEFAULT_REDRAW_MILLIS,
    val heartbeatMillis: Long = DEFAULT_HEARTBEAT_MILLIS,
) {
    companion object {
        const val DEFAULT_SHOW_AFTER_MILLIS = 1_000L
        const val DEFAULT_REDRAW_MILLIS = 100L
        const val DEFAULT_HEARTBEAT_MILLIS = 10_000L
    }
}

/** Where progress is written: raw text to standard error, the width it may use, and whether it is a console. */
internal class ProgressScreen(val columns: Int, val isConsole: Boolean, val write: (String) -> Unit)

/**
 * Shows the progress of a command on standard error: a live status line on a terminal, plain lines elsewhere.
 * [clock] returns nanoseconds from a monotonic source. [tick] must be called regularly while the command runs, so
 * that time keeps moving when no event arrives. Every method is thread-safe.
 */
internal class ProgressRenderer(
    private val style: ProgressStyle,
    private val screen: ProgressScreen,
    private val clock: () -> Long,
    private val timing: ProgressTiming = ProgressTiming(),
) : ProgressListener {
    private val commandStartedAt = clock()
    private val liveLine = LiveLine(screen)
    private var current: StageProgress? = null
    private var lastDrawAt = commandStartedAt
    private var printedLines = 0
    private var closed = false

    @Synchronized
    override fun onEvent(event: ProgressEvent) {
        if (style == ProgressStyle.OFF || closed) return
        when (event) {
            is ProgressEvent.Started -> current = StageProgress(event.stage, clock()).also { draw(it, force = true) }

            is ProgressEvent.Advanced -> {
                val progress = current?.takeIf { it.stage == event.stage } ?: StageProgress(event.stage, clock())
                current = progress.also { it.update(event) }
                draw(progress, force = false)
            }

            is ProgressEvent.Finished -> finish(event)
        }
    }

    @Synchronized
    fun tick() {
        val progress = current
        if (closed || progress == null) return
        when (style) {
            ProgressStyle.LIVE -> draw(progress, force = false)
            ProgressStyle.PLAIN -> heartbeat(progress)
            ProgressStyle.OFF -> Unit
        }
    }

    /** Clears the status line and reports how the command ended. Nothing is shown for a command that was quick. */
    @Synchronized
    fun close(succeeded: Boolean) {
        if (style == ProgressStyle.OFF || closed) return
        closed = true
        liveLine.clear()
        val now = clock()
        if (isVisible(now) || printedLines > 0) {
            screen.write(ProgressLines.summary(succeeded, millisSince(commandStartedAt, now)) + "\n")
        }
    }

    private fun finish(event: ProgressEvent.Finished) {
        val progress = current?.takeIf { it.stage == event.stage } ?: return
        val stageMillis = millisSince(progress.startedAt, clock())
        if (style == ProgressStyle.PLAIN && stageMillis >= timing.showAfterMillis) {
            printLine(ProgressLines.finished(progress, stageMillis))
        }
        current = null
    }

    private fun heartbeat(progress: StageProgress) {
        val now = clock()
        if (millisSince(progress.lastHeartbeatAt, now) < timing.heartbeatMillis) return
        progress.lastHeartbeatAt = now
        printLine(ProgressLines.heartbeat(progress, millisSince(progress.startedAt, now)))
    }

    private fun draw(progress: StageProgress, force: Boolean) {
        val now = clock()
        val due = isVisible(now) && (force || millisSince(lastDrawAt, now) >= timing.redrawMillis)
        if (style != ProgressStyle.LIVE || !due) return
        lastDrawAt = now
        liveLine.show(ProgressLines.live(progress, millisSince(commandStartedAt, now)))
    }

    private fun printLine(line: String) {
        screen.write(line + "\n")
        printedLines++
    }

    private fun isVisible(now: Long): Boolean = millisSince(commandStartedAt, now) >= timing.showAfterMillis
}

private fun millisSince(start: Long, now: Long): Long = TimeUnit.NANOSECONDS.toMillis(now - start)
