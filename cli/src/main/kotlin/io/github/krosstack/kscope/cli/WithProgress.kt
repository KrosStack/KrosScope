package io.github.krosstack.kscope.cli

import io.github.krosstack.kscope.cli.progress.ProgressMode
import io.github.krosstack.kscope.cli.progress.ProgressRenderer
import io.github.krosstack.kscope.cli.progress.ProgressStyle
import io.github.krosstack.kscope.cli.progress.ProgressTiming
import io.github.krosstack.kscope.service.ProgressListener

private const val LIVE_TICK_MILLIS = 100L
private const val PLAIN_TICK_MILLIS = 1_000L

/**
 * Runs [work] with a listener that shows its progress on standard error as [options] ask, and ends the display before
 * returning, so that the result printed afterwards starts on a clean line.
 */
internal fun <T> withProgress(options: ResolvedOptions, work: (ProgressListener) -> T): T {
    val environment = options.environment
    val style = ProgressStyle.choose(options.progress, options.quiet, environment.screen.isConsole)
    if (style == ProgressStyle.OFF) return work(ProgressListener.NONE)
    val timing = if (options.progress == ProgressMode.ALWAYS) ProgressTiming(showAfterMillis = 0) else ProgressTiming()
    val renderer = ProgressRenderer(style, environment.screen, environment.clock, timing)
    val tickMillis = if (style == ProgressStyle.LIVE) LIVE_TICK_MILLIS else PLAIN_TICK_MILLIS
    val ticker = environment.tickers.start(tickMillis, renderer::tick)
    var succeeded = false
    try {
        return work(renderer).also { succeeded = true }
    } finally {
        ticker.close()
        renderer.close(succeeded)
    }
}
