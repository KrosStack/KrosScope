package io.github.krosstack.kscope.benchmark.stats

import java.util.Random
import kotlin.math.ceil

public object PairedBootstrap {
    public fun compare(
        metric: ComparisonMetric,
        pairs: List<PairedValue>,
        candidate: String = "full",
        baseline: String = "lexical",
    ): ComparisonReport {
        val ordered = pairs.sortedBy { it.taskId }
        if (!valid(ordered)) return unmeasured(metric, ordered, candidate, baseline)
        val differences = ordered.map { it.candidate - it.baseline }
        val samples = samples(differences)
        val lower = BootstrapPercentiles.value(samples, BootstrapPolicy.LOWER_PERCENTILE)
        val upper = BootstrapPercentiles.value(samples, BootstrapPolicy.UPPER_PERCENTILE)
        val outcome = outcome(lower, upper)
        return ComparisonReport(
            metric, candidate, baseline, ordered.map { it.candidate }.average(), ordered.map { it.baseline }.average(),
            differences.average(), lower, upper, outcome != ComparisonOutcome.INCONCLUSIVE,
            outcome, ordered, BootstrapSettings(), true,
        )
    }

    private fun valid(pairs: List<PairedValue>): Boolean = pairs.isNotEmpty() &&
        pairs.map { it.taskId }.distinct().size == pairs.size &&
        pairs.all { it.taskId.isNotBlank() && it.candidate.isFinite() && it.baseline.isFinite() }

    private fun samples(differences: List<Double>): DoubleArray {
        val random = Random(BootstrapPolicy.SEED)
        return DoubleArray(BootstrapPolicy.RESAMPLES) {
            var sum = 0.0
            repeat(differences.size) { _ -> sum += differences[random.nextInt(differences.size)] }
            sum / differences.size
        }.also { it.sort() }
    }

    private fun outcome(lower: Double, upper: Double): ComparisonOutcome = when {
        lower > 0.0 -> ComparisonOutcome.SIGNIFICANT_WIN
        upper < 0.0 -> ComparisonOutcome.SIGNIFICANT_LOSS
        else -> ComparisonOutcome.INCONCLUSIVE
    }

    private fun unmeasured(
        metric: ComparisonMetric,
        pairs: List<PairedValue>,
        candidate: String,
        baseline: String,
    ): ComparisonReport = ComparisonReport(
        metric, candidate, baseline, null, null, null, null, null,
        false, ComparisonOutcome.UNMEASURED, pairs, BootstrapSettings(), false,
    )
}

internal object BootstrapPercentiles {
    fun value(ordered: DoubleArray, probability: Double): Double {
        require(ordered.isNotEmpty()) { "A percentile requires at least one sample." }
        require(probability in 0.0..1.0) { "Percentile probability must be between zero and one." }
        val position = (ordered.size - 1) * probability
        val lower = position.toInt()
        val upper = ceil(position).toInt()
        return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)
    }
}
