package io.github.krosstack.kscope.benchmark.localization

import kotlin.math.log2

internal object LocalizationMetrics {
    private const val THREE = 3
    private const val FIVE = 5
    private const val TEN = 10

    fun score(ranked: List<String>, task: LocalizationTask, edits: List<String>): LocalizationScores {
        val gold = task.mustEdit.toSet()
        val unique = ranked.distinct()
        val gains = unique.take(TEN).mapIndexed { index, file -> if (file in gold) 1.0 / log2(index + 2.0) else 0.0 }
        val ideal = (0 until minOf(gold.size, TEN)).sumOf { 1.0 / log2(it + 2.0) }
        val precision = unique.mapIndexed { index, file ->
            if (file in gold) unique.take(index + 1).count { it in gold }.toDouble() / (index + 1) else 0.0
        }.sum()
        return LocalizationScores(
            recall(unique, gold, 1),
            recall(unique, gold, THREE),
            recall(unique, gold, FIVE),
            recall(unique, gold, TEN),
            ratio(gains.sum(), ideal),
            ratio(precision, gold.size.toDouble()),
            ratio(edits.distinct().count { it in gold }.toDouble(), edits.distinct().size.toDouble()),
            recall(unique, gold + task.mustRead, TEN),
        )
    }

    fun mean(scores: List<LocalizationScores>): LocalizationScores = LocalizationScores(
        average(scores.map { it.recall1 }),
        average(scores.map { it.recall3 }),
        average(scores.map { it.recall5 }),
        average(scores.map { it.recall10 }),
        average(scores.map { it.ndcg10 }),
        average(scores.map { it.averagePrecision }),
        average(scores.map { it.editPrecision }),
        average(scores.map { it.contextRecall10 }),
        LocalizationAnalysisMetrics.mean(scores.mapNotNull { it.analysis }),
    )

    private fun recall(ranked: List<String>, gold: Set<String>, count: Int): Double =
        ratio(ranked.take(count).count { it in gold }.toDouble(), gold.size.toDouble())

    private fun ratio(numerator: Double, denominator: Double): Double =
        if (denominator == 0.0) 0.0 else numerator / denominator

    private fun average(values: List<Double>): Double = if (values.isEmpty()) 0.0 else values.average()
}

internal data class LocalizationDimensionScores(val precision: Double, val recall: Double, val exactMatch: Double)

internal object LocalizationSetMetrics {
    fun score(selected: Set<String>, expected: Set<String>): LocalizationDimensionScores {
        val matched = selected.intersect(expected).size
        val precision = when {
            selected.isNotEmpty() -> matched.toDouble() / selected.size
            expected.isEmpty() -> 1.0
            else -> 0.0
        }
        val recall = if (expected.isEmpty()) 1.0 else matched.toDouble() / expected.size
        return LocalizationDimensionScores(precision, recall, if (selected == expected) 1.0 else 0.0)
    }
}
