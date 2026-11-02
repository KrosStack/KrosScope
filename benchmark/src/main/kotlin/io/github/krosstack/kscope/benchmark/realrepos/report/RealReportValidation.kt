package io.github.krosstack.kscope.benchmark.realrepos.report

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.realrepos.RealIdentifier
import io.github.krosstack.kscope.benchmark.realrepos.RealMeasurementShape
import io.github.krosstack.kscope.benchmark.realrepos.RealMeasurementStatus
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryMeasurement
import io.github.krosstack.kscope.model.context.TokenCount

internal data class RealValidation(
    val admitted: List<RealNumericRecord>,
    val issues: Map<RealReportIssue, Int>,
    val recordIssues: Map<String, Set<RealReportIssue>>,
    val valid: Boolean,
    val globalValid: Boolean = valid,
    val budgets: List<TokenCount> = ContextBenchmarkBudgets.values,
)

internal object RealReportValidation {
    fun validate(manifest: RealRepositoryManifest, records: List<RealNumericRecord>): RealValidation {
        val tasks = manifest.tasks.associateBy { it.id }
        val duplicates = records.groupingBy { it.taskId }.eachCount().filterValues { it > 1 }.keys
        val mixed = records.map { it.identity }.distinct().size > 1 || records.any { it.identity.isBlank() }
        val recordedBudgets = records.map { it.budgets }.filter { it.isNotEmpty() }.distinct()
        val mixedBudgets = recordedBudgets.size > 1
        val matched = records.filter { !mixed && !mixedBudgets && it.taskId !in duplicates && it.taskMatches }
        val counts = mapOf(
            RealReportIssue.INVALID_MANIFEST to manifestIssue(manifest),
            RealReportIssue.MIXED_IDENTITY to if (mixed) 1 else 0,
            RealReportIssue.MIXED_BUDGETS to if (mixedBudgets) 1 else 0,
            RealReportIssue.DUPLICATE_RECORD to duplicates.size,
            RealReportIssue.UNKNOWN_TASK to records.count { it.taskId !in tasks },
            RealReportIssue.TASK_MISMATCH to records.count { it.taskId in tasks && !it.taskMatches },
            RealReportIssue.MISSING_RECORD to tasks.keys.count { id -> records.none { it.taskId == id } },
        ).filterValues { it > 0 }
        val recordIssues = matched.associate { it.taskId to it.issues }
        val perRecord = recordIssues.values.flatten().groupingBy { it }.eachCount()
        val issues = (counts + perRecord).toSortedMap()
        val budgets = recordedBudgets.flatten().distinct().sortedBy { it.value }
            .ifEmpty { ContextBenchmarkBudgets.values }
        return RealValidation(
            matched,
            issues,
            recordIssues,
            issues.isEmpty(),
            counts.keys.none { it in GLOBAL },
            budgets,
        )
    }

    private fun manifestIssue(manifest: RealRepositoryManifest): Int {
        val ids = manifest.repositories.map { it.id }
        val valid = ids.isNotEmpty() && ids.distinct().size == ids.size && ids.all(RealIdentifier::isValid) &&
            manifest.tasks.isNotEmpty() && manifest.tasks.map { it.id }.distinct().size == manifest.tasks.size &&
            manifest.tasks.all { it.repository in ids && it.id.isNotBlank() && it.goldFiles.isNotEmpty() }
        return if (valid) 0 else 1
    }

    internal fun issues(record: RealRepositoryMeasurement): Set<RealReportIssue> =
        measurementIssues(record) + completionIssues(record) + localizationIssues(record) + contextIssues(record) +
            setOfNotNull(verification(record))

    private fun measurementIssues(record: RealRepositoryMeasurement): Set<RealReportIssue> = buildSet {
        if (record.status == RealMeasurementStatus.FAILED) add(RealReportIssue.FAILED_RECORD)
        if (record.localization == null || record.context == null || record.scan == null) {
            add(RealReportIssue.MISSING_MEASUREMENT)
        }
        if (record.scan?.diagnostics?.errors?.let { it > 0 } == true) add(RealReportIssue.SCAN_ERROR)
    }

    private fun completionIssues(record: RealRepositoryMeasurement): Set<RealReportIssue> = buildSet {
        if (record.schemaVersion != SCHEMA_VERSION) add(RealReportIssue.UNSUPPORTED_RECORD)
        if (record.status == RealMeasurementStatus.COMPLETE) {
            if (record.update == null) add(RealReportIssue.MISSING_MEASUREMENT)
            val timings = record.timings
            if (listOf(
                    timings.fullScanMillis,
                    timings.localizationMillis,
                    timings.contextMillis,
                    timings.incrementalMillis,
                ).any { it == null }
            ) {
                add(RealReportIssue.MISSING_TIMING)
            }
        }
    }

    private fun localizationIssues(record: RealRepositoryMeasurement): Set<RealReportIssue> =
        if (record.localization != null && RealMeasurementShape.localization(record) == null) {
            setOf(RealReportIssue.INVALID_LOCALIZATION)
        } else {
            emptySet()
        }

    private fun contextIssues(record: RealRepositoryMeasurement): Set<RealReportIssue> = buildSet {
        val unmeasured = RealMeasurementShape.recordedBudgets(record).any {
            RealMeasurementShape.context(record, it) == null
        }
        if (record.context != null && (!RealMeasurementShape.budgets(record) || unmeasured)) {
            add(RealReportIssue.INVALID_CONTEXT)
        }
        if (RealMeasurementShape.sourceIssues(record.context)) add(RealReportIssue.INVALID_SOURCE_ACCOUNTING)
    }

    private fun verification(record: RealRepositoryMeasurement): RealReportIssue? {
        if (!record.task.verify) return null
        val result = record.verification?.verification
        return when {
            record.update == null || result == null -> RealReportIssue.VERIFICATION_MISSING

            record.update.diagnostics.errors > 0 || record.verification.diagnostics.errors > 0 || !result.matches ||
                result.differingUnits > 0 || result.differingSearchDocuments > 0 ->
                RealReportIssue.VERIFICATION_MISMATCH

            else -> null
        }
    }

    private val GLOBAL = setOf(
        RealReportIssue.INVALID_MANIFEST,
        RealReportIssue.MIXED_IDENTITY,
        RealReportIssue.MIXED_BUDGETS,
        RealReportIssue.DUPLICATE_RECORD,
        RealReportIssue.UNKNOWN_TASK,
        RealReportIssue.TASK_MISMATCH,
    )
    private const val SCHEMA_VERSION = 1
}
