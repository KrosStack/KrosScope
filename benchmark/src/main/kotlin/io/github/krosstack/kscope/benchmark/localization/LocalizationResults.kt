package io.github.krosstack.kscope.benchmark.localization

import io.github.krosstack.kscope.benchmark.stats.ComparisonReport
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.RankedSymbol
import io.github.krosstack.kscope.retrieval.RetrievalStage
import kotlinx.serialization.Serializable

@Serializable
public data class LocalizationTask(
    public val id: String,
    public val statement: String,
    public val mustEdit: List<String>,
    public val mustRead: List<String> = emptyList(),
    public val symbols: List<LocalizationSymbolAddress> = emptyList(),
    public val relevantTests: List<LocalizationSymbolAddress>? = null,
    public val changeScope: LocalizationGoldScope? = null,
)

@Serializable
public data class LocalizationStatement(public val id: String, public val statement: String)

@Serializable
public data class LocalizationSymbolAddress(
    public val file: String,
    public val module: String? = null,
    public val sourceSet: String? = null,
    public val fqName: String? = null,
    public val name: String? = null,
    public val kind: String? = null,
    public val line: Int? = null,
    public val signature: String? = null,
)

@Serializable
public data class LocalizationGoldScope(
    public val modules: List<String> = emptyList(),
    public val sourceSets: List<String> = emptyList(),
    public val variants: List<String> = emptyList(),
)

@Serializable
public enum class BenchmarkMethod(public val label: String, public val stage: RetrievalStage? = null) {
    BM25_RAW("BM25 raw text"),
    BM25_ENGLISH("BM25 English stopwords"),
    PATH_ORDER("Path order"),
    LEXICAL("Lexical only", RetrievalStage.LEXICAL),
    GRAPH("+ graph", RetrievalStage.GRAPH),
    PROJECTION("+ source-of-truth projection", RetrievalStage.PROJECTION),
    CATEGORY("+ category prior", RetrievalStage.CATEGORY),
    FULL("KrosScope full", RetrievalStage.FULL),
    ;

    public companion object {
        public val lexicalBaselines: List<BenchmarkMethod> = listOf(BM25_RAW, BM25_ENGLISH, LEXICAL)
    }
}

@Serializable
public enum class BenchmarkSet(public val label: String) {
    ORIGINAL("Original statements"),
    HELD_OUT("Held-out statements"),
}

@Serializable
public enum class LocalizationGatePolicy { STRONGEST_LEXICAL, TWO_TIER }

@Serializable
public data class LocalizationAnalysisScores(
    public val symbolRecall10: Double? = null,
    public val testPrecision: Double? = null,
    public val testRecall: Double? = null,
    public val modulePrecision: Double? = null,
    public val moduleRecall: Double? = null,
    public val moduleExactMatch: Double? = null,
    public val sourceSetPrecision: Double? = null,
    public val sourceSetRecall: Double? = null,
    public val sourceSetExactMatch: Double? = null,
    public val variantPrecision: Double? = null,
    public val variantRecall: Double? = null,
    public val variantExactMatch: Double? = null,
    public val scopeAccuracy: Double? = null,
)

@Serializable
public data class LocalizationScores(
    public val recall1: Double,
    public val recall3: Double,
    public val recall5: Double,
    public val recall10: Double,
    public val ndcg10: Double,
    public val averagePrecision: Double,
    public val editPrecision: Double,
    public val contextRecall10: Double,
    public val analysis: LocalizationAnalysisScores? = null,
)

@Serializable
public data class LocalizationGateScores(
    public val recall5: Double,
    public val recall10: Double,
    public val ndcg10: Double,
)

@Serializable
public data class LocalizationMethodResult(
    public val scores: LocalizationScores,
    public val rankedFiles: List<String>,
    public val selectedFiles: List<String>,
    public val predictedSymbols: List<RankedSymbol> = emptyList(),
    public val selectedTests: List<RankedFile> = emptyList(),
    public val predictedScope: LocalizationGoldScope? = null,
)

@Serializable
public data class TaskLocalization(
    public val id: String,
    public val statement: String,
    public val methods: Map<BenchmarkMethod, LocalizationMethodResult>,
    public val missingGoldFiles: List<String>,
    public val validGold: Boolean = true,
)

@Serializable
public data class LocalizationSetResult(
    public val kind: BenchmarkSet,
    public val tasks: List<TaskLocalization>,
    public val methods: Map<BenchmarkMethod, LocalizationScores>,
    public val strongestLexical: LocalizationGateScores,
    public val gatePassed: Boolean,
    public val testSelectionPassed: Boolean,
    public val symbolRecallPassed: Boolean,
    public val valid: Boolean,
    public val strongestBm25: LocalizationGateScores = LocalizationGateScores(0.0, 0.0, 0.0),
    public val tier2Comparisons: List<ComparisonReport> = emptyList(),
)

@Serializable
public data class LocalizationCorpusSummary(
    public val readableTextFiles: Int = 0,
    public val generatedFiles: Int = 0,
    public val oversizedFiles: Int = 0,
    public val binaryFiles: Int = 0,
    public val unreadableFiles: Int = 0,
)

@Serializable
public data class LocalizationSuiteResult(
    public val sets: List<LocalizationSetResult>,
    public val corpus: LocalizationCorpusSummary,
    public val gatePassed: Boolean,
    public val targetsPassed: Boolean,
    public val valid: Boolean,
    public val complete: Boolean,
    public val validationErrors: List<String> = emptyList(),
    public val policy: LocalizationGatePolicy = LocalizationGatePolicy.STRONGEST_LEXICAL,
    public val schemaVersion: Int = 1,
)
