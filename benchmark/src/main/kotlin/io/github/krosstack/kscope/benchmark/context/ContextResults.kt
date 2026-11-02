package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.benchmark.localization.LocalizationCorpusSummary
import io.github.krosstack.kscope.benchmark.localization.LocalizationSymbolAddress
import io.github.krosstack.kscope.benchmark.localization.LocalizationTask
import io.github.krosstack.kscope.benchmark.stats.ComparisonReport
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.retrieval.RetrievalStage
import kotlinx.serialization.Serializable

@Serializable
public data class ContextGoldSpec(
    public val files: List<String>,
    public val symbols: List<LocalizationSymbolAddress> = emptyList(),
)

@Serializable
public data class ContextBenchmarkTask(
    public val id: String,
    public val statement: String,
    public val gold: ContextGoldSpec,
) {
    public companion object {
        public fun fromLocalization(task: LocalizationTask): ContextBenchmarkTask = ContextBenchmarkTask(
            task.id,
            task.statement,
            ContextGoldSpec(
                (task.mustEdit + task.mustRead + task.symbols.map { it.file }).distinct().sorted(),
                task.symbols,
            ),
        )
    }
}

@Serializable
public enum class ContextBenchmarkMethod(public val label: String, public val stage: RetrievalStage? = null) {
    FULL_COMPILER("Full retrieval compiler", RetrievalStage.FULL),
    LEXICAL_COMPILER("Lexical only compiler", RetrievalStage.LEXICAL),
    GRAPH_COMPILER("Graph compiler", RetrievalStage.GRAPH),
    PROJECTION_COMPILER("Projection compiler", RetrievalStage.PROJECTION),
    CATEGORY_COMPILER("Category compiler", RetrievalStage.CATEGORY),
    BM25_RAW("BM25 raw text"),
    BM25_ENGLISH("BM25 English stopwords"),
    PATH_ORDER("Path order"),
}

@Serializable
public enum class ContextSourceIssueReason {
    UNKNOWN_ARTIFACT,
    NO_RANGE,
    UNSUPPORTED_RENDERING,
    INVALID_RANGE,
    TEXT_MISMATCH,
    TEXT_NOT_RENDERED,
    TOKEN_MISMATCH,
    OVER_BUDGET,
    ESTIMATOR_MISMATCH,
}

@Serializable
public data class ContextSourceIssue(public val fragmentIndex: Int?, public val reason: ContextSourceIssueReason)

@Serializable
public data class ContextScores(
    public val goldContextRecall: Double,
    public val fileRecall: Double,
    public val symbolRecall: Double?,
    public val fullFileRecall: Double,
    public val tokenPrecision: Double,
    public val totalTokens: TokenCount,
    public val goldSourceTokens: TokenCount,
    public val coveredFiles: List<String>,
    public val fullyCoveredFiles: List<String>,
    public val coveredSymbols: List<LocalizationSymbolAddress>,
    public val sourceIssues: List<ContextSourceIssue> = emptyList(),
)

@Serializable
public data class ContextMeanScores(
    public val goldContextRecall: Double,
    public val fileRecall: Double,
    public val symbolRecall: Double?,
    public val fullFileRecall: Double,
    public val tokenPrecision: Double,
    public val meanTotalTokens: Double,
    public val meanGoldSourceTokens: Double,
)

@Serializable
public enum class CoverageCostMeasurement { EXACT_FIXED_ORDER, TESTED_GRID_UPPER_BOUND, NOT_REACHED, UNMEASURED }

@Serializable
public data class FullFileCoverageCosts(
    public val tokens90: TokenCount? = null,
    public val tokens100: TokenCount? = null,
    public val reason90: String? = null,
    public val reason100: String? = null,
    public val measurement90: CoverageCostMeasurement = CoverageCostMeasurement.UNMEASURED,
    public val measurement100: CoverageCostMeasurement = CoverageCostMeasurement.UNMEASURED,
    public val testedBudget90: TokenCount? = null,
    public val testedBudget100: TokenCount? = null,
)

@Serializable
public data class ContextMethodResult(
    public val scores: ContextScores,
    public val context: ContextPackage,
    public val fullFileCoverageCosts: FullFileCoverageCosts,
)

@Serializable
public data class TaskContextResult(
    public val id: String,
    public val statement: String,
    public val gold: ContextGoldSpec,
    public val methods: Map<ContextBenchmarkMethod, ContextMethodResult>,
    public val excludedMissingFiles: List<String> = emptyList(),
    public val unmappedSymbols: List<LocalizationSymbolAddress> = emptyList(),
    public val resolvedGoldFiles: List<String> = emptyList(),
    public val valid: Boolean = true,
)

@Serializable
public data class ContextBudgetResult(
    public val budget: TokenCount,
    public val tasks: List<TaskContextResult>,
    public val methods: Map<ContextBenchmarkMethod, ContextMeanScores>,
    public val tier1Passed: Boolean,
    public val tier2Comparisons: List<ComparisonReport>,
    public val valid: Boolean,
)

@Serializable
public data class ContextSetResult(public val kind: BenchmarkSet, public val budgets: List<ContextBudgetResult>)

@Serializable
public data class ContextBenchmarkResult(
    public val sets: List<ContextSetResult>,
    public val estimator: TokenizerId,
    public val corpus: LocalizationCorpusSummary,
    public val definition: ContextMeasurementDefinition,
    public val tier1Passed: Boolean,
    public val complete: Boolean,
    public val valid: Boolean,
    public val validationErrors: List<String> = emptyList(),
    public val schemaVersion: Int = 1,
)

@Serializable
public data class ContextMeasurementDefinition(
    public val version: String = "source-spans-v1",
    public val filePresence: String =
        "Nonempty verified source-backed FILE, DECLARATION or ENTRY span in an existing gold file",
    public val symbolCoverage: String =
        "Entire actual source declaration span is covered by the union of verified source spans",
    public val tokenPrecision: String =
        "Deduplicated source tokens in gold files divided by exact complete rendered package tokens",
    public val fullFileCost: String =
        "Exact fixed-order complete-file prefix cost, or smallest tested compiler budget and package-token upper bound",
    public val excluded: String =
        "mustCreate, absent files, signatures, outlines and generated text do not supply source coverage",
)
