package io.github.krosstack.kscope.benchmark.realrepos.semantic

import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.service.ScanResult
import kotlinx.serialization.Serializable

@Serializable
internal data class SemanticRecord(
    val task: RealCommitTask,
    val pipeline: String,
    val binding: String,
    val rebuilt: Boolean = false,
    val embedded: Int = 0,
    val reused: Int = 0,
    val seconds: Map<String, Double> = emptyMap(),
    val queries: Map<String, Query> = emptyMap(),
    val failure: String? = null,
    val scan: ScanResult? = null,
) {
    @Serializable
    data class Query(val text: String?, val rankings: Map<String, List<String>>, val metrics: Map<String, List<Double>>)
}
