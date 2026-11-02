package io.github.krosstack.kscope.benchmark.realrepos.report

import io.github.krosstack.kscope.benchmark.context.ContextMeanScores
import io.github.krosstack.kscope.benchmark.localization.LocalizationScores
import io.github.krosstack.kscope.benchmark.stats.ComparisonRows
import java.util.Locale

internal object RealReportMarkdown {
    fun render(report: RealRepositoryReport): String = buildString {
        appendLine("# Real repository benchmark")
        appendLine()
        appendLine("Tier 1: ${status(report.tier1Passed)}. Complete: ${report.complete}. Valid: ${report.valid}.")
        appendLine("Context budgets in this report: ${report.budgets.joinToString { it.value.toString() }}.")
        appendLine("A report that lacks any of the four standard context budgets cannot pass Tier 1.")
        appendLine("Task statements, source content and paths remain outside this aggregate report.")
        appendLine("DEV and TEST remain separate. Pooled means weight commits equally, not repositories equally.")
        appendLine(
            "Partial groups show available observations with explicit expected and missing counts; they cannot pass.",
        )
        appendLine("Tier 1 strictly beats raw and English BM25; lexical-only paired intervals are nonblocking Tier 2.")
        appendLine("Commit-only gold has no symbol/test targets: both are UNMEASURED.")
        appendLine(
            "Corpus counts sum snapshot observations, not unique repository files. " +
                "Timing distributions count available samples.",
        )
        appendLine()
        appendLine("Validation categories: ${report.validationIssues.entries.joinToString { "${it.key}=${it.value}" }}")
        report.eligibilityExclusions.forEach { (repository, exclusions) ->
            appendLine(
                "$repository eligibility exclusions: ${exclusions.entries.joinToString { "${it.key}=${it.value}" }}",
            )
        }
        report.groups.forEach { appendGroup(it) }
        if (report.classGroups.isNotEmpty()) {
            appendLine()
            appendLine("# Results by task class")
            RealClassTables.append(this, report.classGroups)
            report.classGroups.forEach { appendGroup(it) }
        }
    }

    private fun StringBuilder.appendGroup(group: RealGroupReport) {
        appendLine()
        appendLine("## ${group.taskClass?.label ?: group.repository ?: "Pooled"} / ${group.split}")
        appendLine()
        appendLine(
            "Expected ${group.expected}; recorded ${group.recorded}; measured ${group.measured}; " +
                "missing ${group.missing}; failed ${group.failed}; rejected ${group.rejected}.",
        )
        appendLine(
            "Tier 1 ${status(
                group.tier1Passed,
            )}; complete ${group.complete}; valid ${group.valid}; partial ${group.partial}.",
        )
        appendLine("Failures: ${group.failures.entries.joinToString { "${it.key}=${it.value}" }}")
        appendLine("Failure phases: ${group.failurePhases.entries.joinToString { "${it.key}=${it.value}" }}")
        appendLine(
            "Gold files absent from the parent index (counted as misses): ${group.goldAbsentFromIndex.files} " +
                "in ${group.goldAbsentFromIndex.tasks} tasks.",
        )
        appendTimings(group)
        appendVerification(group.verification)
        appendLocalization(group.localization)
        group.context.forEach { appendContext(it) }
        appendLine()
        appendLine(
            "Snapshot corpus: ${group.corpus.readableTextFiles} readable; ${group.corpus.generatedFiles} generated;",
        )
        appendLine(
            "${group.corpus.oversizedFiles} oversized; ${group.corpus.binaryFiles} binary; " +
                "${group.corpus.unreadableFiles} unreadable.",
        )
    }

    private fun StringBuilder.appendTimings(group: RealGroupReport) {
        appendLine()
        appendLine("| Timing | Samples | Mean ms | Median ms | p95 ms |")
        appendLine("|---|---|---|---|---|")
        group.timings.forEach { (kind, value) ->
            appendLine(
                "| $kind | ${value.samples} | ${number(value.meanMillis)} | ${number(value.medianMillis)} | " +
                    "${number(value.p95Millis)} |",
            )
        }
    }

    private fun StringBuilder.appendVerification(value: RealVerificationSummary) {
        appendLine()
        appendLine("Verification: ${value.expected} expected; ${value.recorded} recorded; ${value.matches} matching;")
        appendLine("${value.failed} failed; ${value.unavailable} unavailable; ${value.differingUnits} differing units;")
        appendLine("${value.differingSearchDocuments} differing search documents.")
    }

    private fun StringBuilder.appendLocalization(value: RealLocalizationReport) {
        appendLine()
        appendLine("### Localization / ${value.observedTasks} observed tasks")
        appendLine()
        appendLine("| Method | R@1 | R@3 | R@5 | R@10 | NDCG@10 | MAP | Edit P | Context R@10 |")
        appendLine("|---|---|---|---|---|---|---|---|---|")
        value.methods.forEach { (method, score) -> appendLine(localization(method.label, score)) }
        appendLine()
        append(ComparisonRows.markdown(value.tier2Comparisons))
    }

    private fun StringBuilder.appendContext(value: RealContextReport) {
        appendLine()
        appendLine("### Context ${value.budget.value} / ${value.observedTasks} observed tasks")
        appendLine()
        appendLine(
            "| Method | Gold recall | File recall | Full-file recall | Token precision | Mean tokens | " +
                "Mean gold tokens |",
        )
        appendLine("|---|---|---|---|---|---|---|")
        value.methods.forEach { (method, score) -> appendLine(context(method.label, score)) }
        appendLine()
        append(ComparisonRows.markdown(value.tier2Comparisons))
    }

    private fun localization(name: String, score: LocalizationScores?): String = row(
        name,
        listOf(
            score?.recall1,
            score?.recall3,
            score?.recall5,
            score?.recall10,
            score?.ndcg10,
            score?.averagePrecision,
            score?.editPrecision,
            score?.contextRecall10,
        ),
    )

    private fun context(name: String, score: ContextMeanScores?): String = row(
        name,
        listOf(
            score?.goldContextRecall,
            score?.fileRecall,
            score?.fullFileRecall,
            score?.tokenPrecision,
            score?.meanTotalTokens,
            score?.meanGoldSourceTokens,
        ),
    )

    private fun row(name: String, values: List<Double?>): String = "| $name | ${values.joinToString(
        " | ",
        transform = ::number,
    )} |"
    private fun number(value: Double?): String = value?.let { String.format(Locale.ROOT, "%.4f", it) } ?: "N/A"
    private fun status(passed: Boolean): String = if (passed) "PASS" else "FAIL"
}
