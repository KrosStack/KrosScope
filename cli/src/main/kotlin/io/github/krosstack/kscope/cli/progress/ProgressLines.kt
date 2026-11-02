package io.github.krosstack.kscope.cli.progress

import io.github.krosstack.kscope.cli.text.format.Durations
import io.github.krosstack.kscope.cli.text.format.Format

/** The words of the status line and of the plain lines. */
internal object ProgressLines {
    fun counts(progress: StageProgress): String {
        val total = progress.total
        val unit = ProgressLabels.unit(progress.unit, total ?: progress.done)
        val done = Format.count(progress.done)
        return when {
            !progress.hasCounts() -> ""
            total == null || total == progress.done -> "$done $unit"
            else -> "$done of ${Format.count(total)} $unit (${Format.percent(progress.done, total)})"
        }
    }

    fun live(progress: StageProgress, elapsedMillis: Long): String {
        val counts = counts(progress)
        val label = ProgressLabels.stage(progress.stage)
        val state = if (counts.isEmpty()) label else "$label: $counts"
        return "$state - ${Durations.human(elapsedMillis)}"
    }

    fun finished(progress: StageProgress, stageMillis: Long): String {
        val counts = counts(progress)
        val label = ProgressLabels.stage(progress.stage)
        val duration = "(${Durations.human(stageMillis)})"
        return if (counts.isEmpty()) "$label $duration" else "$label: $counts $duration"
    }

    fun heartbeat(progress: StageProgress, stageMillis: Long): String {
        val counts = counts(progress)
        val label = ProgressLabels.stage(progress.stage)
        val running = "still running (${Durations.human(stageMillis)})"
        return if (counts.isEmpty()) "$label: $running" else "$label: $counts, $running"
    }

    fun summary(succeeded: Boolean, totalMillis: Long): String {
        val duration = Durations.human(totalMillis)
        return if (succeeded) "Done in $duration." else "Stopped after $duration."
    }
}
