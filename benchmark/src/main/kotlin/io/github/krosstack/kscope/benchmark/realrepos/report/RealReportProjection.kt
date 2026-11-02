package io.github.krosstack.kscope.benchmark.realrepos.report

import io.github.krosstack.kscope.benchmark.context.ContextGoldSpec
import io.github.krosstack.kscope.benchmark.context.ContextMethodResult
import io.github.krosstack.kscope.benchmark.context.ContextScores
import io.github.krosstack.kscope.benchmark.context.FullFileCoverageCosts
import io.github.krosstack.kscope.benchmark.context.TaskContextResult
import io.github.krosstack.kscope.benchmark.localization.LocalizationCorpusSummary
import io.github.krosstack.kscope.benchmark.localization.LocalizationMethodResult
import io.github.krosstack.kscope.benchmark.localization.TaskLocalization
import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealMeasurementShape
import io.github.krosstack.kscope.benchmark.realrepos.RealMeasurementStatus
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryMeasurement
import io.github.krosstack.kscope.benchmark.realrepos.RealTimings
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.service.VerificationResult

internal data class RealNumericRecord(
    val taskId: String,
    val identity: String,
    val taskMatches: Boolean,
    val status: RealMeasurementStatus,
    val timings: RealTimings,
    val corpus: LocalizationCorpusSummary?,
    val localization: TaskLocalization?,
    val context: Map<TokenCount, TaskContextResult>,
    val verification: VerificationResult?,
    val issues: Set<RealReportIssue>,
    val failurePhase: RealFailurePhase,
    val absentGold: Int = 0,
    val budgets: Set<TokenCount> = emptySet(),
)

internal object RealReportProjection {
    fun of(record: RealRepositoryMeasurement, expected: RealCommitTask?): RealNumericRecord = RealNumericRecord(
        record.task.id, record.identity, record.task == expected, record.status, record.timings, record.corpus,
        RealMeasurementShape.localization(record)?.let(::localization),
        RealMeasurementShape.recordedBudgets(record).sortedBy { it.value }.mapNotNull { budget ->
            RealMeasurementShape.context(record, budget)?.let { budget to context(it) }
        }.toMap(),
        record.verification?.verification?.copy(differences = emptyList()),
        RealReportValidation.issues(record), phase(record.failure), absentGold(record),
        RealMeasurementShape.recordedBudgets(record),
    )

    private fun absentGold(record: RealRepositoryMeasurement): Int {
        val localization = record.localization?.tasks?.singleOrNull()?.missingGoldFiles.orEmpty()
        val context = record.context?.budgets?.firstOrNull()?.tasks?.singleOrNull()?.excludedMissingFiles.orEmpty()
        return (localization + context).distinct().size
    }

    private fun localization(task: TaskLocalization): TaskLocalization = TaskLocalization(
        task.id,
        "",
        task.methods.mapValues { (_, result) ->
            LocalizationMethodResult(result.scores.copy(analysis = null), emptyList(), emptyList())
        },
        emptyList(),
        task.validGold,
    )

    private fun context(task: TaskContextResult): TaskContextResult {
        val resolved = task.resolvedGoldFiles.size
        val share = resolved.toDouble() / (resolved + task.excludedMissingFiles.size)
        return TaskContextResult(
            task.id,
            "",
            ContextGoldSpec(emptyList()),
            task.methods.mapValues { (_, result) ->
                val old = result.context
                val placeholder = ContextPackage("", old.level, old.budget, old.estimator, old.totalTokens, emptyList())
                val scores = withFullGold(result.scores, share).copy(
                    coveredFiles = emptyList(),
                    fullyCoveredFiles = emptyList(),
                    coveredSymbols = emptyList(),
                )
                ContextMethodResult(scores, placeholder, FullFileCoverageCosts())
            },
        )
    }

    private fun withFullGold(scores: ContextScores, share: Double): ContextScores = if (share == 1.0) {
        scores
    } else {
        scores.copy(
            goldContextRecall = scores.goldContextRecall * share,
            fileRecall = scores.fileRecall * share,
            fullFileRecall = scores.fullFileRecall * share,
        )
    }

    private fun phase(failure: String?): RealFailurePhase = when (failure?.substringBefore(':')) {
        "snapshot" -> RealFailurePhase.SNAPSHOT
        "parent-checkout" -> RealFailurePhase.PARENT_CHECKOUT
        "parent-scan" -> RealFailurePhase.PARENT_SCAN
        "localization" -> RealFailurePhase.LOCALIZATION
        "context" -> RealFailurePhase.CONTEXT
        "child-checkout" -> RealFailurePhase.CHILD_CHECKOUT
        "incremental-update" -> RealFailurePhase.INCREMENTAL_UPDATE
        "verification" -> RealFailurePhase.VERIFICATION
        else -> RealFailurePhase.OTHER
    }
}
