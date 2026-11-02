package io.github.krosstack.kscope.service

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

/** The stages that queries report to the progress listener of [OpenOptions]. */
public object QueryStages {
    public val LOAD_INDEX: ProgressStage = ProgressStage("load-index")
    public val RANK: ProgressStage = ProgressStage("rank")
    public val ESTIMATE: ProgressStage = ProgressStage("estimate-context")
    public val COMPILE: ProgressStage = ProgressStage("compile-context")
    public val SEARCH: ProgressStage = ProgressStage("search-symbols")
    public val IMPACT: ProgressStage = ProgressStage("trace-impact")
    public val RELATED: ProgressStage = ProgressStage("follow-relationships")
    public val GRAPH_VIEW: ProgressStage = ProgressStage("build-graph")
}

internal inline fun <T> ProgressListener.stage(stage: ProgressStage, block: () -> T): T {
    onEvent(ProgressEvent.Started(stage))
    try {
        return block()
    } finally {
        onEvent(ProgressEvent.Finished(stage))
    }
}
