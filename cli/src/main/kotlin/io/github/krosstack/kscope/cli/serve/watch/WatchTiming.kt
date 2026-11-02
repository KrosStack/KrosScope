package io.github.krosstack.kscope.cli.serve.watch

internal data class WatchTiming(
    val pollMillis: Long = 2_000,
    val quietMillis: Long = 3_000,
    val minimumGapMillis: Long = 5_000,
    val retryMillis: Long = 10_000,
    val maximumRetryMillis: Long = 300_000,
    val maximumPollMillis: Long = 60_000,
) {
    init {
        require(pollMillis > 0)
        require(quietMillis > 0)
        require(minimumGapMillis >= 0)
        require(retryMillis > 0)
        require(maximumRetryMillis >= retryMillis)
        require(maximumPollMillis >= pollMillis)
    }
}
