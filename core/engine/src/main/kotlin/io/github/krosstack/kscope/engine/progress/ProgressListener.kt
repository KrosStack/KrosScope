package io.github.krosstack.kscope.engine.progress

/** A named step of a long operation, such as `discovery` or `production`. */
@JvmInline
public value class ProgressStage(public val id: String)

/** What a long operation reports while it runs. Every event names the stage it belongs to. */
public sealed interface ProgressEvent {
    public val stage: ProgressStage

    public data class Started(override val stage: ProgressStage) : ProgressEvent

    /** [done] of [total] [unit]s are complete; [total] is null while it is not known. */
    public data class Advanced(
        override val stage: ProgressStage,
        public val done: Long,
        public val total: Long?,
        public val unit: String,
    ) : ProgressEvent

    public data class Finished(override val stage: ProgressStage) : ProgressEvent
}

/**
 * Receives the progress of a long operation. The default does nothing, so reporting never changes a result.
 * Events may arrive from any thread; an implementation must be thread-safe and quick.
 */
public fun interface ProgressListener {
    public fun onEvent(event: ProgressEvent)

    public companion object {
        public val NONE: ProgressListener = ProgressListener { }
    }
}

/** Reports [stage] as started before [block] runs and as finished after it, also when [block] throws. */
public inline fun <T> ProgressListener.stage(stage: ProgressStage, block: () -> T): T {
    onEvent(ProgressEvent.Started(stage))
    try {
        return block()
    } finally {
        onEvent(ProgressEvent.Finished(stage))
    }
}

/** Reports that [done] of [total] [unit]s of [stage] are complete. */
public fun ProgressListener.advance(stage: ProgressStage, done: Long, total: Long?, unit: String) {
    onEvent(ProgressEvent.Advanced(stage, done, total, unit))
}

/** The stages of a scan, in the order they run. Their ids are also the keys of the stage timings. */
public object ScanStages {
    public val DISCOVERY: ProgressStage = ProgressStage("discovery")
    public val BUILD_MODEL: ProgressStage = ProgressStage("build-model")
    public val SOURCE_SETS: ProgressStage = ProgressStage("source-sets")
    public val SYNTHESIS: ProgressStage = ProgressStage("synthesis")
    public val PRODUCTION: ProgressStage = ProgressStage("production")
    public val LINKING: ProgressStage = ProgressStage("linking")
    public val CLEANUP: ProgressStage = ProgressStage("cleanup")
    public val SEARCH_INDEX: ProgressStage = ProgressStage("search-index")

    public const val VERIFY_PREFIX: String = "verify-"
}

/** Passes every event on with its stage renamed to `verify-<id>`, for the clean rebuild that `--verify` compares to. */
internal fun ProgressListener.forVerification(): ProgressListener = ProgressListener { event ->
    val stage = ProgressStage(ScanStages.VERIFY_PREFIX + event.stage.id)
    onEvent(
        when (event) {
            is ProgressEvent.Started -> event.copy(stage = stage)
            is ProgressEvent.Advanced -> event.copy(stage = stage)
            is ProgressEvent.Finished -> event.copy(stage = stage)
        },
    )
}
