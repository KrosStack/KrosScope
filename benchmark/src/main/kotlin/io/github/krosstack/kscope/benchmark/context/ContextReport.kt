package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.stats.ComparisonRows
import io.github.krosstack.kscope.model.context.TokenCount
import java.util.Locale

public object ContextReport {
    public fun markdown(result: ContextBenchmarkResult): String = buildString {
        appendLine("# Context benchmark")
        appendLine()
        val measured = measuredBudgets(result)
        val partial = measured != ContextBenchmarkBudgets.values
        appendVerdict(result, measured, partial)
        appendLine("Valid: ${result.valid}.")
        appendLine("Estimator: ${result.estimator.value}. Definition: ${result.definition.version}.")
        result.validationErrors.forEach { appendLine("Validation error: $it") }
        appendDefinition(result.definition)
        result.sets.forEach { set ->
            appendLine()
            appendLine("## ${set.kind.label}")
            set.budgets.forEach { appendBudget(it, partial) }
        }
    }

    private fun measuredBudgets(result: ContextBenchmarkResult): List<TokenCount> =
        result.sets.flatMap { set -> set.budgets.map { it.budget } }.distinct().sortedBy { it.value }

    private fun StringBuilder.appendVerdict(
        result: ContextBenchmarkResult,
        measured: List<TokenCount>,
        partial: Boolean,
    ) {
        if (!partial) {
            appendLine("Tier 1: ${status(result.tier1Passed)}. Both sets measured: ${result.complete}.")
            return
        }
        appendLine("Tier 1: NOT EVALUATED. Both sets measured: ${result.complete}.")
        appendLine("Budgets measured: ${measured.joinToString { it.value.toString() }}.")
        appendLine("A partial-budget run is not a Tier 1 evaluation; Tier 1 needs all four standard budgets:")
        appendLine(ContextBenchmarkBudgets.values.joinToString(prefix = "", postfix = ".") { it.value.toString() })
    }

    private fun StringBuilder.appendDefinition(definition: ContextMeasurementDefinition) {
        appendLine()
        appendLine("Tier 1 strictly beats raw and English BM25 in gold-context recall and token precision")
        appendLine("at every budget on both sets. Path order is diagnostic; lexical packing is a Tier 2 comparison.")
        appendLine("Macro averages weight tasks equally. Combined recall counts distinct files plus gold symbols.")
        appendLine("File presence: ${definition.filePresence}.")
        appendLine("Symbol coverage: ${definition.symbolCoverage}.")
        appendLine("Precision: ${definition.tokenPrecision}.")
        appendLine("Full-file cost: ${definition.fullFileCost}.")
        appendLine("Exclusions: ${definition.excluded}.")
        appendLine("Overlapping and adjacent source ranges are merged before numerator tokenization.")
        appendLine("Headers, fences, warnings and other rendered metadata remain in the denominator.")
        appendLine("Compiler and lexical packing use the same recommended-level compiler and estimator.")
        appendLine("Raw/English/path pack complete files in ranking order, then truncate the last source prefix.")
        appendLine("All methods count the same wrapper; generated, signature and outline text earns no body credit.")
        appendLine("Compiler 90/100 costs report the smallest tested budget that reaches full source coverage,")
        appendLine("plus package tokens, as a tested-grid upper bound. Greedy selection can be nonmonotonic.")
        appendLine("Fixed-order methods report exact complete-prefix costs when coverage is reachable.")
        appendLine("Spent paraphrases are a read-only regression set and cannot justify tuning.")
    }

    private fun StringBuilder.appendBudget(result: ContextBudgetResult, partial: Boolean) {
        appendLine()
        appendLine("### Budget ${result.budget.value}")
        appendLine()
        val label = if (partial) "Tier 1 criteria at this budget" else "Tier 1"
        appendLine("$label: ${status(result.tier1Passed)}. Valid: ${result.valid}.")
        appendLine()
        appendLine(
            "| Task / method | Gold recall | File recall | Symbol recall | Full-file recall | " +
                "Token precision | Tokens | Gold tokens |",
        )
        appendLine("|---|---|---|---|---|---|---|---|")
        result.methods.forEach { (method, scores) -> appendLine(ContextRows.mean("All / ${method.label}", scores)) }
        result.tasks.forEach { task ->
            task.methods.forEach { (method, measured) ->
                appendLine(ContextRows.task("${task.id} / ${method.label}", measured.scores))
            }
        }
        appendLine()
        appendLine("#### Tier 2: full compiler versus lexical compiler")
        appendLine()
        append(ComparisonRows.markdown(result.tier2Comparisons))
        appendCosts(result)
        appendIssues(result)
    }

    private fun StringBuilder.appendCosts(result: ContextBudgetResult) {
        appendLine()
        appendLine("#### Tokens for full gold-file source coverage")
        appendLine()
        appendLine(
            "| Task / method | 90% tokens | 90% budget | 90% measure | " +
                "100% tokens | 100% budget | 100% measure | Reason |",
        )
        appendLine("|---|---|---|---|---|---|---|---|")
        result.tasks.forEach { task ->
            task.methods.forEach { (method, measured) ->
                val costs = measured.fullFileCoverageCosts
                val reason = listOfNotNull(costs.reason90, costs.reason100).distinct().joinToString(" ")
                val ninety = "${costs.tokens90?.value ?: "N/A"} | " +
                    "${costs.testedBudget90?.value ?: "N/A"} | ${costs.measurement90}"
                val full = "${costs.tokens100?.value ?: "N/A"} | " +
                    "${costs.testedBudget100?.value ?: "N/A"} | ${costs.measurement100}"
                appendLine("| ${task.id} / ${method.label} | $ninety | $full | $reason |")
            }
        }
    }

    private fun StringBuilder.appendIssues(result: ContextBudgetResult) {
        val issues = result.tasks.filter { task ->
            task.excludedMissingFiles.isNotEmpty() || task.unmappedSymbols.isNotEmpty() ||
                task.methods.values.any { it.scores.sourceIssues.isNotEmpty() }
        }
        if (issues.isEmpty()) return
        appendLine()
        appendLine("#### Source accounting issues")
        appendLine()
        issues.forEach { task ->
            appendLine("${task.id}: excluded absent/unreadable files ${task.excludedMissingFiles.joinToString()};")
            appendLine("unmapped gold declaration spans ${task.unmappedSymbols.size}.")
            task.methods.filter { it.value.scores.sourceIssues.isNotEmpty() }.forEach { (method, measured) ->
                appendLine("${method.label}: ${measured.scores.sourceIssues.joinToString { it.reason.name }}")
            }
        }
    }

    private fun status(passed: Boolean): String = if (passed) "PASS" else "FAIL"
}

private object ContextRows {
    fun mean(name: String, score: ContextMeanScores): String = row(
        name,
        listOf(
            score.goldContextRecall,
            score.fileRecall,
            score.symbolRecall,
            score.fullFileRecall,
            score.tokenPrecision,
            score.meanTotalTokens,
            score.meanGoldSourceTokens,
        ),
    )

    fun task(name: String, score: ContextScores): String = row(
        name,
        listOf(
            score.goldContextRecall,
            score.fileRecall,
            score.symbolRecall,
            score.fullFileRecall,
            score.tokenPrecision,
            score.totalTokens.value.toDouble(),
            score.goldSourceTokens.value.toDouble(),
        ),
    )

    private fun row(name: String, values: List<Double?>): String {
        val cells = values.joinToString(" | ") { value ->
            value?.let { String.format(Locale.ROOT, "%.4f", it) } ?: "N/A"
        }
        return "| $name | $cells |"
    }
}
