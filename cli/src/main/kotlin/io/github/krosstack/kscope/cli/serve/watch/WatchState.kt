package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.model.Fingerprint
import java.time.Instant
import java.util.concurrent.TimeUnit

internal class WatchState(
    private val settings: WatchSettings,
    private val clock: () -> Long,
    private val stamp: () -> Fingerprint,
    private val git: () -> Boolean,
) {
    private val timing = settings.timing
    private val retry = WatchRetry(timing)
    private var baseline: Fingerprint? = null
    private var scanStamp: Fingerprint? = null
    private var observed: Fingerprint? = null
    private var stableSince: Long? = null
    private var stableThrough: Long? = null
    private var gitPaused = false
    private var scanning = false
    private var trusted = false
    private var stampFailed = false
    private var nextAutomaticAt: Long? = null
    private var lastUpdate: Instant? = null
    private var updates = 0L
    private var intervalMillis = timing.pollMillis
    private var eligibilityStale = false
    private val deepening = WatchDeepening(timing)
    val freshness = WatchFreshness(timing.quietMillis)
    val stopped: Boolean @Synchronized get() = retry.stopped
    var shallow: Boolean
        @Synchronized get() = deepening.shallow

        @Synchronized set(value) {
            deepening.observe(value)
        }

    /** Whether a look at the files has passed since the trust and the depth of the index were read. */
    val eligibilityCheckWanted: Boolean @Synchronized get() = eligibilityStale
    val updateWanted: Boolean @Synchronized get() = ready()
    private val upToDate: Boolean
        get() = baseline != null && observed == baseline && !stampFailed && !deepening.shallow
    private val quietAndDue: Boolean
        get() {
            val stable = stableSince ?: return false
            val now = clock()
            val quiet = (stableThrough ?: stable) - stable >= TimeUnit.MILLISECONDS.toNanos(freshness.quietMillis)
            val gap = nextAutomaticAt?.let { now >= it } ?: true
            return quiet && gap && now >= maxOf(retry.nextAttempt, deepening.nextAttempt)
        }

    @Synchronized
    fun poll(): Long {
        if (retry.stopped || scanning) return intervalMillis
        if (!stampFailed || clock() >= retry.nextAttempt) {
            runCatching {
                val current = readStamp()
                observe(current, git())
                stampFailed = false
            }.onFailure {
                stampFailed = true
                retry.fail(clock())
                stableSince = null
                stableThrough = null
            }
        }
        return if (stampFailed) maxOf(retry.delayMillis, intervalMillis) else intervalMillis
    }

    @Synchronized
    fun beginUpdate(): Boolean {
        if (!ready()) return false
        val paused = git()
        if (paused) {
            gitPaused = true
            stableSince = null
            stableThrough = null
        } else {
            scanStamp = observed
            scanning = true
            deepening.started()
        }
        return !paused
    }

    @Synchronized
    fun setTrusted(value: Boolean) {
        trusted = value
        eligibilityStale = false
    }

    @Synchronized
    fun scanStarted() {
        if (scanning) return
        scanStamp = runCatching { readStamp() }.getOrNull()
        scanning = true
    }

    @Synchronized
    fun scanCompleted(requiresTrust: Boolean, success: Boolean = true) {
        scanning = false
        if (!success) return
        trusted = requiresTrust
        baseline = scanStamp
        observed = scanStamp
        stableSince = null
        stableThrough = null
        stampFailed = scanStamp == null
        if (stampFailed) {
            if (!retry.stopped) retry.fail(clock())
            return
        }
        lastUpdate = settings.wallClock()
        if (!retry.stopped) retry.reset()
    }

    @Synchronized
    fun updateCompleted(code: Int, requiresTrust: Boolean) {
        scanning = false
        if (code == 0) {
            scanCompleted(requiresTrust)
            if (!stampFailed) updates++
        } else {
            retry.fail(clock())
        }
        deepening.ended(succeeded = code == 0, now = clock())
        nextAutomaticAt = clock() + TimeUnit.MILLISECONDS.toNanos(timing.minimumGapMillis)
    }

    @Synchronized
    fun reason(): WatchReason? = when {
        upToDate -> null
        else -> WatchReasoning(freshness, retry, deepening, trusted, gitPaused).reason()
    }

    @Synchronized
    fun status(): String {
        if (retry.stopped) return "Watcher: stopped after failures; ${WatchReason.STOPPED.text}."
        return when (val reason = reason()) {
            null -> "Watcher: up to date; last update ${lastUpdate ?: "none"}; $updates automatic updates."
            else -> "Watcher: behind; ${reason.text}."
        }
    }

    private fun observe(current: Fingerprint, paused: Boolean) {
        val now = clock()
        val changed = current != observed
        if (changed) deepening.restart()
        if (paused || gitPaused || changed) stableSince = null
        observed = current
        gitPaused = paused
        if (!paused && stableSince == null) stableSince = now
        stableThrough = if (paused) null else now
        eligibilityStale = true
    }

    private fun readStamp(): Fingerprint {
        val started = clock()
        return try {
            stamp()
        } finally {
            val elapsed = (clock() - started).coerceAtLeast(0)
            val maximum = TimeUnit.MILLISECONDS.toNanos(timing.maximumPollMillis)
            val stretched = elapsed.coerceAtMost(maximum) * POLLING_COST_MULTIPLIER
            intervalMillis = TimeUnit.NANOSECONDS.toMillis(stretched.coerceAtMost(maximum))
                .coerceAtLeast(timing.pollMillis)
        }
    }

    private fun ready(): Boolean {
        val unblocked = !scanning && !retry.stopped && !freshness.holdsUpdates
        val changed = (observed != baseline || deepening.wanted) && !stampFailed
        return unblocked && !trusted && !gitPaused && changed && quietAndDue
    }

    private companion object {
        const val POLLING_COST_MULTIPLIER = 20
    }
}
