package io.github.krosstack.kscope.benchmark.realrepos.report

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.context.ContextMeanScores
import io.github.krosstack.kscope.benchmark.localization.BenchmarkMethod
import io.github.krosstack.kscope.benchmark.localization.LocalizationScores
import java.util.Locale

internal object RealClassTables {
    private val localizationMethods =
        listOf(BenchmarkMethod.FULL, BenchmarkMethod.BM25_ENGLISH, BenchmarkMethod.BM25_RAW)
    private val contextMethods = listOf(
        ContextBenchmarkMethod.FULL_COMPILER,
        ContextBenchmarkMethod.BM25_ENGLISH,
        ContextBenchmarkMethod.BM25_RAW,
    )

    fun append(out: StringBuilder, groups: List<RealGroupReport>) {
        out.appendLine()
        out.appendLine("Each cell is KrosScope / BM25 with English stop words removed / BM25 on raw text.")
        out.appendLine()
        out.appendLine("| Class | Split | Tasks | Measured | Recall@5 | Recall@10 | NDCG@10 |")
        out.appendLine("|---|---|---:|---:|---|---|---|")
        groups.forEach { group ->
            out.appendLine(
                "| ${label(group)} | ${group.split} | ${group.expected} | ${group.measured} | " +
                    "${localization(group, LocalizationScores::recall5)} | " +
                    "${localization(group, LocalizationScores::recall10)} | " +
                    "${localization(group, LocalizationScores::ndcg10)} |",
            )
        }
        appendContext(out, groups, "Gold recall of a context package", ContextMeanScores::goldContextRecall)
        appendContext(out, groups, "Token precision of a context package", ContextMeanScores::tokenPrecision)
    }

    private fun appendContext(
        out: StringBuilder,
        groups: List<RealGroupReport>,
        title: String,
        metric: (ContextMeanScores) -> Double,
    ) {
        val budgets = groups.firstOrNull()?.context?.map { it.budget.value }.orEmpty()
        out.appendLine()
        out.appendLine("$title:")
        out.appendLine()
        out.appendLine("| Class | Split | ${budgets.joinToString(" | ") { "%,d tokens".format(Locale.ROOT, it) }} |")
        out.appendLine("|---|---|${budgets.joinToString("") { "---|" }}")
        groups.forEach { group ->
            val cells = group.context.joinToString(" | ") { report ->
                contextMethods.joinToString(" / ") { number(report.methods[it]?.let(metric)) }
            }
            out.appendLine("| ${label(group)} | ${group.split} | $cells |")
        }
    }

    private fun localization(group: RealGroupReport, metric: (LocalizationScores) -> Double): String =
        localizationMethods.joinToString(" / ") { number(group.localization.methods[it]?.let(metric)) }

    private fun label(group: RealGroupReport): String = group.taskClass?.label.orEmpty()

    private fun number(value: Double?): String = value?.let { String.format(Locale.ROOT, "%.4f", it) } ?: "N/A"
}
