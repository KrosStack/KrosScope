package io.github.krosstack.kscope.benchmark.realrepos.report

import io.github.krosstack.kscope.benchmark.context.ContextAggregates
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkOptions
import io.github.krosstack.kscope.benchmark.context.ContextMeanScores
import io.github.krosstack.kscope.benchmark.localization.BenchmarkMethod
import io.github.krosstack.kscope.benchmark.localization.LocalizationBenchmark
import io.github.krosstack.kscope.benchmark.localization.LocalizationCorpusSummary
import io.github.krosstack.kscope.benchmark.localization.LocalizationScores
import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealMeasurementStatus
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.model.context.TokenCount
import java.security.MessageDigest
import java.util.HexFormat

internal object RealGroupAggregates {
    fun group(
        selector: RealGroupSelector,
        manifest: RealRepositoryManifest,
        records: List<RealNumericRecord>,
        validation: RealValidation,
    ): RealGroupReport {
        val (repository, split, taskClass) = selector
        val expected = manifest.tasks.filter(selector::matches)
        val ids = expected.map { it.id }.toSet()
        val present = records.filter { it.taskId in ids }
        val admitted = validation.admitted.filter { it.taskId in ids }
        val failures = admitted.flatMap { validation.recordIssues[it.taskId].orEmpty() }.groupingBy { it }.eachCount()
        val complete = expected.isNotEmpty() && admitted.size == expected.size &&
            admitted.all { it.status == RealMeasurementStatus.COMPLETE }
        val valid = complete && failures.isEmpty() && validation.globalValid
        val local = localization(admitted, valid)
        val context = validation.budgets.map { context(admitted, it, valid) }
        return RealGroupReport(
            repository, split, expected.size, present.size, measured(admitted, validation.budgets),
            ids.count { id -> present.none { it.taskId == id } },
            present.count {
                it.status ==
                    RealMeasurementStatus.FAILED
            },
            present.size - admitted.size, complete, valid, !valid,
            valid && local.tier1Passed && context.all { it.tier1Passed },
            failures.toSortedMap(),
            phases(
                present,
            ),
            corpus(admitted), absentGold(admitted), RealTimingStats.of(admitted), verification(expected, admitted),
            local, context, taskClass,
        )
    }

    private fun localization(records: List<RealNumericRecord>, valid: Boolean): RealLocalizationReport {
        val tasks = records.mapNotNull { record ->
            record.localization?.copy(id = publicId(record.taskId))
        }
        val aggregate = tasks.takeIf { it.isNotEmpty() }?.let(LocalizationBenchmark::aggregate)
        val methods: Map<BenchmarkMethod, LocalizationScores?> = BenchmarkMethod.entries.associateWith { method ->
            aggregate?.methods?.getValue(method)?.copy(analysis = null)
        }
        return RealLocalizationReport(
            tasks.size,
            methods,
            valid && aggregate?.gatePassed == true,
            aggregate?.tier2Comparisons.orEmpty(),
        )
    }

    private val measured = ContextBenchmarkOptions.DEFAULT_METHODS

    private fun context(records: List<RealNumericRecord>, budget: TokenCount, valid: Boolean): RealContextReport {
        val tasks = records.mapNotNull { record ->
            record.context[budget]?.copy(id = publicId(record.taskId))
        }
        val aggregate = tasks.takeIf { it.isNotEmpty() }?.let {
            ContextAggregates.budget(budget, it, measured)
        }
        val methods: Map<ContextBenchmarkMethod, ContextMeanScores?> = measured.associateWith {
            aggregate?.methods?.getValue(it)
        }
        return RealContextReport(
            budget,
            tasks.size,
            methods,
            valid && aggregate?.tier1Passed == true,
            aggregate?.tier2Comparisons.orEmpty(),
        )
    }

    private fun measured(records: List<RealNumericRecord>, budgets: List<TokenCount>): Int = records.count {
        it.localization != null && it.context.keys == budgets.toSet()
    }

    private fun corpus(records: List<RealNumericRecord>): LocalizationCorpusSummary {
        val values = records.mapNotNull { it.corpus }
        return LocalizationCorpusSummary(
            values.sumOf { it.readableTextFiles },
            values.sumOf { it.generatedFiles },
            values.sumOf { it.oversizedFiles },
            values.sumOf { it.binaryFiles },
            values.sumOf { it.unreadableFiles },
        )
    }

    private fun absentGold(records: List<RealNumericRecord>): RealAbsentGold =
        RealAbsentGold(records.count { it.absentGold > 0 }, records.sumOf { it.absentGold })

    private fun verification(
        expected: List<RealCommitTask>,
        records: List<RealNumericRecord>,
    ): RealVerificationSummary {
        val planned = expected.count { it.verify }
        val plannedIds = expected.filter { it.verify }.map { it.id }.toSet()
        val present = records.filter { it.taskId in plannedIds }.mapNotNull { it.verification }
        return RealVerificationSummary(
            planned,
            present.size,
            present.count { it.matches },
            present.count { !it.matches },
            planned - present.size,
            present.sumOf { it.differingUnits },
            present.sumOf { it.differingSearchDocuments },
        )
    }

    private fun phases(records: List<RealNumericRecord>): Map<RealFailurePhase, Int> = records
        .filter {
            it.status == RealMeasurementStatus.FAILED
        }.map { it.failurePhase }.groupingBy { it }.eachCount().toSortedMap()

    private fun publicId(taskId: String): String = "task-" + HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(taskId.toByteArray(Charsets.UTF_8)),
    )
}
