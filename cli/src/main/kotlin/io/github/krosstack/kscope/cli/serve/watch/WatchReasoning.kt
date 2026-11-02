package io.github.krosstack.kscope.cli.serve.watch

internal class WatchReasoning(
    private val freshness: WatchFreshness,
    private val retry: WatchRetry,
    private val deepening: WatchDeepening,
    private val trusted: Boolean,
    private val gitPaused: Boolean,
) {
    fun reason(): WatchReason = when {
        retry.stopped -> WatchReason.STOPPED
        freshness.invalid -> WatchReason.SETTINGS
        !freshness.watching -> WatchReason.OFF
        trusted -> WatchReason.TRUSTED
        gitPaused -> WatchReason.GIT
        retry.failures > 0 -> WatchReason.RETRY
        else -> deepening.reason ?: WatchReason.SETTLING
    }
}
