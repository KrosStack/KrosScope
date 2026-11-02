package io.github.krosstack.kscope.benchmark.localization

import io.github.krosstack.kscope.benchmark.stats.ComparisonMetric
import io.github.krosstack.kscope.benchmark.stats.ComparisonReport
import io.github.krosstack.kscope.benchmark.stats.PairedBootstrap
import io.github.krosstack.kscope.benchmark.stats.PairedValue

internal object LocalizationComparisons {
    private val metrics = listOf(ComparisonMetric.RECALL_5, ComparisonMetric.RECALL_10, ComparisonMetric.NDCG_10)

    fun lexical(tasks: List<TaskLocalization>): List<ComparisonReport> = metrics.map { metric ->
        val pairs = tasks.map { task ->
            PairedValue(
                task.id,
                value(task.methods.getValue(BenchmarkMethod.FULL).scores, metric),
                value(task.methods.getValue(BenchmarkMethod.LEXICAL).scores, metric),
            )
        }
        PairedBootstrap.compare(metric, pairs)
    }

    fun strongestLexical(methods: Map<BenchmarkMethod, LocalizationScores>): LocalizationGateScores =
        strongest(methods, BenchmarkMethod.lexicalBaselines)

    fun strongestBm25(methods: Map<BenchmarkMethod, LocalizationScores>): LocalizationGateScores =
        strongest(methods, listOf(BenchmarkMethod.BM25_RAW, BenchmarkMethod.BM25_ENGLISH))

    private fun strongest(
        methods: Map<BenchmarkMethod, LocalizationScores>,
        baselines: List<BenchmarkMethod>,
    ): LocalizationGateScores {
        val scores = baselines.map { methods.getValue(it) }
        return LocalizationGateScores(
            scores.maxOf { it.recall5 },
            scores.maxOf { it.recall10 },
            scores.maxOf { it.ndcg10 },
        )
    }

    private fun value(scores: LocalizationScores, metric: ComparisonMetric): Double = when (metric) {
        ComparisonMetric.RECALL_5 -> scores.recall5

        ComparisonMetric.RECALL_10 -> scores.recall10

        ComparisonMetric.NDCG_10 -> scores.ndcg10

        ComparisonMetric.GOLD_CONTEXT_RECALL, ComparisonMetric.TOKEN_PRECISION ->
            error("A context metric cannot score localization.")
    }
}
