package io.github.krosstack.kscope.benchmark.localization

import io.github.krosstack.kscope.benchmark.stats.ComparisonRows
import io.github.krosstack.kscope.spi.search.EnglishStopWords
import java.util.Locale

public object LocalizationReport {
    public fun markdown(result: LocalizationSuiteResult): String = buildString {
        appendLine("# Task localization")
        appendLine()
        appendLine("Gate: ${status(result.gatePassed)}. Policy: ${result.policy}. Valid corpus: ${result.valid}.")
        appendLine("Both sets measured: ${result.complete}. Supplemental targets: ${status(result.targetsPassed)}.")
        result.validationErrors.forEach { appendLine("Validation error: $it") }
        appendMethods(result.corpus, result.policy)
        result.sets.forEach { appendSet(it, result.policy) }
        appendDefinitions()
    }

    private fun StringBuilder.appendMethods(corpus: LocalizationCorpusSummary, policy: LocalizationGatePolicy) {
        appendLine()
        appendLine("Macro averages over statements; binary file relevance is mustEdit.")
        when (policy) {
            LocalizationGatePolicy.STRONGEST_LEXICAL ->
                appendLine("Historical gate: strictly beat the strongest lexical value per metric in both sets.")

            LocalizationGatePolicy.TWO_TIER -> {
                appendLine("Tier 1: strictly beat raw and English BM25 on R@5, R@10 and NDCG@10 in both sets.")
                appendLine("Tier 2: paired FULL-minus-LEXICAL intervals and quality targets do not block Tier 1.")
            }
        }
        appendLine("Lexical baselines: raw-text BM25, English-stopword BM25, and KrosScope lexical only.")
        appendLine("All BM25 methods use SQLite FTS5 porter/unicode61 stemming.")
        appendLine("English removes ${EnglishStopWords.words.size} standard stopwords from documents and queries.")
        appendLine("Lexical only shares KrosScope's code tokenizer, vocabulary bridge and path/symbol boosts.")
        appendLine("Ablations add graph, projection and category prior in order; KrosScope full also adds history.")
        appendLine("Raw and English share readable, non-generated UTF-8 text at most 2 MiB, excluding NUL bytes.")
        appendLine("The code index reads source, build, resource, schema, config and documentation content;")
        appendLine("other categories, including native code, contribute paths only.")
        appendLine("Constants must be frozen before held-out scoring. New files are not localization targets.")
        appendLine("Held-out input accepts a statement file or a directory containing statements.json.")
        appendLine()
        appendLine("Raw corpus: ${corpus.readableTextFiles} text files; omitted ${corpus.generatedFiles} generated,")
        appendLine("${corpus.oversizedFiles} oversized, ${corpus.binaryFiles} binary,")
        appendLine("${corpus.unreadableFiles} unreadable files.")
    }

    private fun StringBuilder.appendDefinitions() {
        appendLine()
        appendLine("Raw/path baselines select 10 files; retrieval stages use adaptive edit selection.")
        appendLine("JSON includes ranked/selected paths, statement text and missing gold paths.")
        appendLine("Read files affect context recall. Absent supplemental gold is N/A. Tests compare distinct files.")
        appendLine("Scope compares module paths, source-set names and variant names independently and overall.")
        appendLine("Empty gold retains recall one and penalizes extras; empty prediction and gold score one.")
        appendLine("Symbol addresses match file, name and line; signatures disambiguate when no line is supplied.")
        appendLine("Raw/path baselines do not predict symbols, tests or scope: these are N/A.")
    }

    private fun StringBuilder.appendSet(result: LocalizationSetResult, policy: LocalizationGatePolicy) {
        appendLine()
        appendLine("## ${result.kind.label}")
        appendLine()
        appendLine("Localization: ${status(result.gatePassed)}; test selection: ${status(result.testSelectionPassed)};")
        appendLine("symbol recall: ${status(result.symbolRecallPassed)}. Valid corpus: ${result.valid}.")
        appendLine("Targets: test P >= 0.5 with R >= 0.8; symbol R@10 >= 0.5.")
        appendLine()
        appendLine("| Task / method | R@1 | R@3 | R@5 | R@10 | NDCG@10 | MAP | Edit P | Edit+read R@10 |")
        appendLine("|---|---|---|---|---|---|---|---|---|")
        result.methods.forEach { (method, scores) ->
            appendLine(LocalizationRows.files("All / ${method.label}", scores))
        }
        result.tasks.forEach { task ->
            task.methods.forEach { (method, measured) ->
                appendLine(LocalizationRows.files("${task.id} / ${method.label}", measured.scores))
            }
        }
        appendGateComparison(result, policy)
        if (policy == LocalizationGatePolicy.TWO_TIER) appendTier2(result)
        appendAnalysis(result)
        appendScope(result)
    }

    private fun StringBuilder.appendGateComparison(result: LocalizationSetResult, policy: LocalizationGatePolicy) {
        appendLine()
        appendLine("### Gate comparisons and losses")
        appendLine()
        appendLine("Each baseline cell is the per-metric maximum for this policy; ties do not pass.")
        appendLine()
        appendLine("| Task | K R@5 | Baseline R@5 | K R@10 | Baseline R@10 | K NDCG@10 | Baseline NDCG@10 |")
        appendLine("|---|---|---|---|---|---|---|")
        val kscope = result.methods.getValue(BenchmarkMethod.FULL)
        val aggregateBaseline = baseline(result.methods, policy)
        appendLine(LocalizationRows.gate("All", kscope, aggregateBaseline))
        result.tasks.forEach { task ->
            val scores = task.methods.mapValues { it.value.scores }
            val strongest = baseline(scores, policy)
            appendLine(LocalizationRows.gate(task.id, scores.getValue(BenchmarkMethod.FULL), strongest))
        }
    }

    private fun baseline(
        methods: Map<BenchmarkMethod, LocalizationScores>,
        policy: LocalizationGatePolicy,
    ): LocalizationGateScores = when (policy) {
        LocalizationGatePolicy.STRONGEST_LEXICAL -> LocalizationComparisons.strongestLexical(methods)
        LocalizationGatePolicy.TWO_TIER -> LocalizationComparisons.strongestBm25(methods)
    }

    private fun StringBuilder.appendTier2(result: LocalizationSetResult) {
        appendLine()
        appendLine("### Tier 2: full retrieval versus lexical only")
        appendLine()
        append(ComparisonRows.markdown(result.tier2Comparisons))
    }

    private fun StringBuilder.appendAnalysis(result: LocalizationSetResult) {
        appendLine()
        appendLine("### Symbol and test selection")
        appendLine()
        appendLine("| Task / method | Symbol R@10 | Test P | Test R |")
        appendLine("|---|---|---|---|")
        result.methods.forEach { (method, scores) ->
            appendLine(LocalizationRows.analysis("All / ${method.label}", scores.analysis))
        }
        result.tasks.forEach { task ->
            task.methods.forEach { (method, measured) ->
                appendLine(LocalizationRows.analysis("${task.id} / ${method.label}", measured.scores.analysis))
            }
        }
    }

    private fun StringBuilder.appendScope(result: LocalizationSetResult) {
        appendLine()
        appendLine("### Change scope")
        appendLine()
        appendLine(
            "| Task / method | Module P | Module R | Module exact | Source set P | Source set R | " +
                "Source set exact | Variant P | Variant R | Variant exact | Overall exact |",
        )
        appendLine("|---|---|---|---|---|---|---|---|---|---|---|")
        result.methods.forEach { (method, scores) ->
            appendLine(LocalizationRows.scope("All / ${method.label}", scores.analysis))
        }
        result.tasks.forEach { task ->
            task.methods.forEach { (method, measured) ->
                appendLine(LocalizationRows.scope("${task.id} / ${method.label}", measured.scores.analysis))
            }
        }
    }

    private fun status(passed: Boolean): String = if (passed) "PASS" else "FAIL"
}

private object LocalizationRows {
    fun analysis(name: String, scores: LocalizationAnalysisScores?): String = row(
        name,
        listOf(scores?.symbolRecall10, scores?.testPrecision, scores?.testRecall),
    )

    fun scope(name: String, scores: LocalizationAnalysisScores?): String = row(
        name,
        listOf(
            scores?.modulePrecision,
            scores?.moduleRecall,
            scores?.moduleExactMatch,
            scores?.sourceSetPrecision,
            scores?.sourceSetRecall,
            scores?.sourceSetExactMatch,
            scores?.variantPrecision,
            scores?.variantRecall,
            scores?.variantExactMatch,
            scores?.scopeAccuracy,
        ),
    )

    fun gate(name: String, scores: LocalizationScores, baseline: LocalizationGateScores): String = row(
        name,
        listOf(scores.recall5, baseline.recall5, scores.recall10, baseline.recall10, scores.ndcg10, baseline.ndcg10),
    )

    fun files(name: String, scores: LocalizationScores): String = row(
        name,
        listOf(
            scores.recall1,
            scores.recall3,
            scores.recall5,
            scores.recall10,
            scores.ndcg10,
            scores.averagePrecision,
            scores.editPrecision,
            scores.contextRecall10,
        ),
    )

    private fun row(name: String, values: List<Double?>): String {
        val cells = values.joinToString(" | ") { value ->
            value?.let { String.format(Locale.ROOT, "%.4f", it) } ?: "N/A"
        }
        return "| $name | $cells |"
    }
}
