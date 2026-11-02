package io.github.krosstack.kscope.cli.serve.watch

/**
 * Whether the index is shallower than an automatic scan would make it, and how often the watcher still tries. A scan
 * that succeeds can leave the index shallow, for instance when the build of a trusted workspace fails and the scan
 * falls back to the static model, so a successful update does not end the deepening. Attempts that leave the index
 * shallow are counted here, apart from failed updates, and back off like them until the watcher stops trying. A
 * change of the files, or an index that becomes shallow again, starts over.
 */
internal class WatchDeepening(timing: WatchTiming) {
    private val retry = WatchRetry(timing)
    private var shallowAtStart = false

    var shallow = false
        private set

    val wanted: Boolean get() = shallow && !retry.stopped
    val reason: WatchReason?
        get() = when {
            !shallow -> null
            retry.stopped -> WatchReason.DEEPENING_STOPPED
            retry.failures > 0 -> WatchReason.DEEPENING_RETRY
            else -> WatchReason.DEEPENING
        }
    val nextAttempt: Long get() = if (shallow) retry.nextAttempt else Long.MIN_VALUE

    fun observe(value: Boolean) {
        if (value && !shallow) retry.reset()
        shallow = value
    }

    fun started() {
        shallowAtStart = shallow
    }

    fun ended(succeeded: Boolean, now: Long) {
        if (succeeded && shallowAtStart && shallow) retry.fail(now)
        shallowAtStart = false
    }

    fun restart() = retry.reset()
}
