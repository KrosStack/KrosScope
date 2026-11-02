package io.github.krosstack.kscope.benchmark.stats

import java.util.Locale

internal object ComparisonRows {
    fun markdown(comparisons: List<ComparisonReport>): String = buildString {
        appendLine("| Metric | Candidate mean | Baseline mean | Paired delta | Lower 95% | Upper 95% | Outcome |")
        appendLine("|---|---|---|---|---|---|---|")
        comparisons.forEach { comparison ->
            val numbers = listOf(
                comparison.candidateMean,
                comparison.baselineMean,
                comparison.pointDelta,
                comparison.lower95,
                comparison.upper95,
            ).joinToString(" | ") { decimal(it) }
            appendLine("| ${comparison.metric} | $numbers | ${comparison.outcome} |")
        }
        appendLine()
        appendLine("Paired percentile bootstrap: ${BootstrapPolicy.RESAMPLES} resamples,")
        appendLine("fixed seed ${BootstrapPolicy.SEED}.")
        appendLine("Intervals use linear interpolation at the 2.5th and 97.5th percentiles.")
        appendLine("Zero is excluded only for a significant win or loss. Each task has equal weight.")
        appendLine("Task pairs and settings are in JSON so intervals can be reproduced.")
    }

    private fun decimal(value: Double?): String = value?.let { String.format(Locale.ROOT, "%.4f", it) } ?: "N/A"
}
