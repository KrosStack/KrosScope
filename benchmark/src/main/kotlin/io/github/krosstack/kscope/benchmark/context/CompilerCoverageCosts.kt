package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.model.context.TokenCount

internal object CompilerCoverageCosts {
    fun annotate(
        measured: Map<TokenCount, TaskContextResult>,
        methods: List<ContextBenchmarkMethod>,
    ): Map<TokenCount, TaskContextResult> {
        val costs = methods.filter { it.stage != null }.associateWith { method -> costs(measured, method) }
        return measured.mapValues { (_, task) ->
            task.copy(
                methods = task.methods.mapValues { (method, result) ->
                    costs[method]?.let { result.copy(fullFileCoverageCosts = it) } ?: result
                },
            )
        }
    }

    private fun costs(
        measured: Map<TokenCount, TaskContextResult>,
        method: ContextBenchmarkMethod,
    ): FullFileCoverageCosts {
        val ordered = measured.entries.sortedBy { it.key.value }
        val ninety = ordered.firstOrNull { it.value.methods.getValue(method).scores.fullFileRecall >= NINETY }
        val full = ordered.firstOrNull { it.value.methods.getValue(method).scores.fullFileRecall >= 1.0 }
        val through = ordered.lastOrNull()?.key?.value ?: 0
        return FullFileCoverageCosts(
            ninety?.value?.methods?.getValue(method)?.scores?.totalTokens,
            full?.value?.methods?.getValue(method)?.scores?.totalTokens,
            reason(ninety != null, through),
            reason(full != null, through),
            measurement(ninety != null),
            measurement(full != null),
            ninety?.key,
            full?.key,
        )
    }

    private fun reason(reached: Boolean, through: Int): String = if (reached) {
        "Smallest tested budget achieving coverage; this is a tested-grid upper bound, not a global minimum."
    } else {
        "Full-file coverage was not reached through $through tested tokens."
    }

    private fun measurement(reached: Boolean): CoverageCostMeasurement = if (reached) {
        CoverageCostMeasurement.TESTED_GRID_UPPER_BOUND
    } else {
        CoverageCostMeasurement.NOT_REACHED
    }

    private const val NINETY = 0.9
}
