package io.github.krosstack.kscope.benchmark.realrepos.report

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.context.ContextMeanScores
import io.github.krosstack.kscope.benchmark.localization.BenchmarkMethod
import io.github.krosstack.kscope.benchmark.localization.LocalizationCorpusSummary
import io.github.krosstack.kscope.benchmark.localization.LocalizationScores
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.stats.ComparisonReport
import io.github.krosstack.kscope.model.context.TokenCount
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
internal enum class RealReportIssue {
    INVALID_MANIFEST,
    MIXED_IDENTITY,
    MIXED_BUDGETS,
    DUPLICATE_RECORD,
    UNKNOWN_TASK,
    TASK_MISMATCH,
    MISSING_RECORD,
    FAILED_RECORD,
    MISSING_MEASUREMENT,
    MISSING_TIMING,
    UNSUPPORTED_RECORD,
    INVALID_LOCALIZATION,
    INVALID_CONTEXT,
    INVALID_SOURCE_ACCOUNTING,
    SCAN_ERROR,
    VERIFICATION_MISSING,
    VERIFICATION_MISMATCH,
}

@Serializable
internal enum class RealTimingKind { FULL_SCAN, INCREMENTAL, LOCALIZATION, CONTEXT, VERIFICATION }

@Serializable
internal enum class RealFailurePhase {
    SNAPSHOT,
    PARENT_CHECKOUT,
    PARENT_SCAN,
    LOCALIZATION,
    CONTEXT,
    CHILD_CHECKOUT,
    INCREMENTAL_UPDATE,
    VERIFICATION,
    OTHER,
}

@Serializable
internal data class RealTimingSummary(
    val samples: Int,
    val meanMillis: Double?,
    val medianMillis: Double?,
    val p95Millis: Double?,
)

@Serializable
internal data class RealAbsentGold(val tasks: Int, val files: Int)

@Serializable
internal data class RealVerificationSummary(
    val expected: Int,
    val recorded: Int,
    val matches: Int,
    val failed: Int,
    val unavailable: Int,
    val differingUnits: Int,
    val differingSearchDocuments: Int,
)

@Serializable
internal data class RealLocalizationReport(
    val observedTasks: Int,
    val methods: Map<BenchmarkMethod, LocalizationScores?>,
    val tier1Passed: Boolean,
    val tier2Comparisons: List<ComparisonReport>,
)

@Serializable
internal data class RealContextReport(
    val budget: TokenCount,
    val observedTasks: Int,
    val methods: Map<ContextBenchmarkMethod, ContextMeanScores?>,
    val tier1Passed: Boolean,
    val tier2Comparisons: List<ComparisonReport>,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class RealGroupReport(
    val repository: String?,
    val split: RealSplit,
    val expected: Int,
    val recorded: Int,
    val measured: Int,
    val missing: Int,
    val failed: Int,
    val rejected: Int,
    val complete: Boolean,
    val valid: Boolean,
    val partial: Boolean,
    val tier1Passed: Boolean,
    val failures: Map<RealReportIssue, Int>,
    val failurePhases: Map<RealFailurePhase, Int>,
    val corpus: LocalizationCorpusSummary,
    val goldAbsentFromIndex: RealAbsentGold,
    val timings: Map<RealTimingKind, RealTimingSummary>,
    val verification: RealVerificationSummary,
    val localization: RealLocalizationReport,
    val context: List<RealContextReport>,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val taskClass: RealTaskClass? = null,
)

@Serializable
internal enum class RealTargetStatus { UNMEASURED, }

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class RealRepositoryReport(
    val groups: List<RealGroupReport>,
    val eligibilityExclusions: Map<String, Map<String, Int>>,
    val validationIssues: Map<RealReportIssue, Int>,
    val complete: Boolean,
    val valid: Boolean,
    val tier1Passed: Boolean,
    val budgets: List<TokenCount> = emptyList(),
    val symbolTarget: RealTargetStatus = RealTargetStatus.UNMEASURED,
    val testTarget: RealTargetStatus = RealTargetStatus.UNMEASURED,
    val schemaVersion: Int = 1,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val classGroups: List<RealGroupReport> = emptyList(),
)
