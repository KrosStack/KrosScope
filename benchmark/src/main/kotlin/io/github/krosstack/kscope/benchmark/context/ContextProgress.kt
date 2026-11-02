package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.model.context.TokenCount

public enum class ContextProgressBoundary {
    SET_STARTED,
    SET_FINISHED,
    TASK_STARTED,
    TASK_FINISHED,
    ANALYSIS_STARTED,
    ANALYSIS_FINISHED,
    COVERAGE_COST_STARTED,
    COVERAGE_COST_FINISHED,
    BUDGET_STARTED,
    BUDGET_FINISHED,
    METHOD_STARTED,
    METHOD_FINISHED,
    CHECKPOINT_SAVED,
    CHECKPOINT_LOADED,
}

public data class ContextProgressEvent(
    public val boundary: ContextProgressBoundary,
    public val set: BenchmarkSet,
    public val taskId: String? = null,
    public val budget: TokenCount? = null,
    public val method: ContextBenchmarkMethod? = null,
)

internal class ContextProgressTrace(
    private val set: BenchmarkSet,
    private val taskId: String,
    private val callback: (ContextProgressEvent) -> Unit,
) {
    fun emit(boundary: ContextProgressBoundary, budget: TokenCount? = null, method: ContextBenchmarkMethod? = null) {
        callback(ContextProgressEvent(boundary, set, taskId, budget, method))
    }
}
