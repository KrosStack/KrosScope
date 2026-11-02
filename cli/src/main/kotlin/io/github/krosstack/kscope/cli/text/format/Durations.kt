package io.github.krosstack.kscope.cli.text.format

private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_TEN_SECONDS = 10_000L
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 3_600_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val HALF_SECOND = 500L

/** Durations in the unit a person reads: milliseconds, seconds with a decimal, then minutes and hours. */
internal object Durations {
    fun human(millis: Long): String = when {
        millis < MILLIS_PER_SECOND -> "$millis ms"
        millis < MILLIS_PER_TEN_SECONDS -> "${Format.decimal(millis / MILLIS_PER_SECOND.toDouble(), 1)} s"
        millis < MILLIS_PER_MINUTE -> "${seconds(millis)} s"
        millis < MILLIS_PER_HOUR -> minutesAndSeconds(seconds(millis))
        else -> "${millis / MILLIS_PER_HOUR} h ${millis / MILLIS_PER_MINUTE % MINUTES_PER_HOUR} m"
    }

    private fun seconds(millis: Long): Long = (millis + HALF_SECOND) / MILLIS_PER_SECOND

    private fun minutesAndSeconds(seconds: Long): String =
        "${seconds / SECONDS_PER_MINUTE} m ${seconds % SECONDS_PER_MINUTE} s"
}
