package io.github.krosstack.kscope.benchmark.context.identity

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.retrieval.MlMode

public data class ContextBenchmarkSelection(
    public val methods: List<ContextBenchmarkMethod>,
    public val pipeline: ContextPipelineIdentity? = null,
) {
    init {
        require(methods.isNotEmpty() && methods.distinct().size == methods.size) { "Benchmark methods must be unique." }
        require(methods.none { it.ml == MlMode.ON } || pipeline != null) { "ON methods require a pipeline identity." }
    }

    public val analysisKeys: List<ContextAnalysisKey>
        get() = methods.mapNotNull(::analysisKey).distinct()

    public fun analysisKey(method: ContextBenchmarkMethod): ContextAnalysisKey? = method.stage?.let {
        ContextAnalysisKey(it, method.ml, if (method.ml == MlMode.ON) requireNotNull(pipeline).fingerprint() else null)
    }
}
