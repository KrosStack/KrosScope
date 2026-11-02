package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.stats.ComparisonMetric
import io.github.krosstack.kscope.benchmark.stats.ComparisonReport
import io.github.krosstack.kscope.benchmark.stats.PairedBootstrap
import io.github.krosstack.kscope.benchmark.stats.PairedValue
import io.github.krosstack.kscope.model.context.TokenCount

internal object ContextAggregates {
    fun budget(
        budget: TokenCount,
        tasks: List<TaskContextResult>,
        methods: List<ContextBenchmarkMethod>,
    ): ContextBudgetResult {
        val mean = methods.associateWith { method -> mean(tasks.map { it.methods.getValue(method).scores }) }
        val valid = tasks.isNotEmpty() && tasks.all { it.valid } && tasks.map { it.id }.distinct().size == tasks.size
        return ContextBudgetResult(budget, tasks, mean, valid && tier1(mean), lexical(tasks), valid)
    }

    private fun mean(scores: List<ContextScores>): ContextMeanScores = ContextMeanScores(
        average(scores.map { it.goldContextRecall }),
        average(scores.map { it.fileRecall }),
        scores.mapNotNull { it.symbolRecall }.takeIf { it.isNotEmpty() }?.average(),
        average(scores.map { it.fullFileRecall }),
        average(scores.map { it.tokenPrecision }),
        average(scores.map { it.totalTokens.value.toDouble() }),
        average(scores.map { it.goldSourceTokens.value.toDouble() }),
    )

    private fun tier1(methods: Map<ContextBenchmarkMethod, ContextMeanScores>): Boolean {
        val full = methods[ContextBenchmarkMethod.FULL_COMPILER] ?: return false
        val baselines = listOf(ContextBenchmarkMethod.BM25_RAW, ContextBenchmarkMethod.BM25_ENGLISH)
            .mapNotNull(methods::get)
        return baselines.size == 2 && baselines.all {
            full.goldContextRecall > it.goldContextRecall && full.tokenPrecision > it.tokenPrecision
        }
    }

    private fun lexical(tasks: List<TaskContextResult>): List<ComparisonReport> {
        val pairable = tasks.all {
            ContextBenchmarkMethod.FULL_COMPILER in it.methods && ContextBenchmarkMethod.LEXICAL_COMPILER in it.methods
        }
        return if (!pairable) {
            emptyList()
        } else {
            listOf(ComparisonMetric.GOLD_CONTEXT_RECALL, ComparisonMetric.TOKEN_PRECISION)
                .map { metric -> PairedBootstrap.compare(metric, pairs(tasks, metric)) }
        }
    }

    private fun pairs(tasks: List<TaskContextResult>, metric: ComparisonMetric): List<PairedValue> = tasks.map { task ->
        PairedValue(
            task.id,
            value(task, ContextBenchmarkMethod.FULL_COMPILER, metric),
            value(task, ContextBenchmarkMethod.LEXICAL_COMPILER, metric),
        )
    }

    private fun value(task: TaskContextResult, method: ContextBenchmarkMethod, metric: ComparisonMetric): Double {
        val score = task.methods.getValue(method).scores
        return when (metric) {
            ComparisonMetric.GOLD_CONTEXT_RECALL -> score.goldContextRecall

            ComparisonMetric.TOKEN_PRECISION -> score.tokenPrecision

            ComparisonMetric.RECALL_5, ComparisonMetric.RECALL_10, ComparisonMetric.NDCG_10 ->
                error("A localization metric cannot score context.")
        }
    }

    private fun average(values: List<Double>): Double = if (values.isEmpty()) 0.0 else values.average()
}
