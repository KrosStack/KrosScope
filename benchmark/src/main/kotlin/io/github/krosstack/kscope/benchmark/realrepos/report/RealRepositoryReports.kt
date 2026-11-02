package io.github.krosstack.kscope.benchmark.realrepos.report

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.realrepos.RealIdentifier
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryMeasurement
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.corpus.CorpusExclusions
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

internal object RealRepositoryReports {
    fun aggregate(manifest: RealRepositoryManifest, records: List<RealRepositoryMeasurement>): RealRepositoryReport {
        val tasks = manifest.tasks.associateBy { it.id }
        val projected = records.map { RealReportProjection.of(it, tasks[it.task.id]) }
        val validation = RealReportValidation.validate(manifest, projected)
        val repositories = manifest.repositories.map { it.id }.filter(RealIdentifier::isValid).distinct().sorted()
        val groups = (repositories.map { it as String? } + listOf(null)).flatMap { repository ->
            splitsOf(manifest, repository).map { split ->
                RealGroupAggregates.group(RealGroupSelector(repository, split), manifest, projected, validation)
            }
        }
        val complete = groups.isNotEmpty() && groups.all { it.complete }
        val valid = validation.valid && groups.all { it.valid }
        return RealRepositoryReport(
            groups,
            exclusions(manifest, repositories),
            validation.issues,
            complete,
            valid,
            complete && valid && groups.all { it.tier1Passed } && validation.budgets == ContextBenchmarkBudgets.values,
            validation.budgets,
            classGroups = classGroups(manifest, projected, validation),
        )
    }

    private fun splitsOf(manifest: RealRepositoryManifest, repository: String?): List<RealSplit> =
        RealSplit.entries.filter { split -> manifest.tasks.any { RealGroupSelector(repository, split).matches(it) } }

    private fun classGroups(
        manifest: RealRepositoryManifest,
        records: List<RealNumericRecord>,
        validation: RealValidation,
    ): List<RealGroupReport> = RealTaskClass.of(manifest.tasks).flatMap { taskClass ->
        RealSplit.entries.filter { split -> manifest.tasks.any { it.split == split && taskClass.matches(it) } }
            .map { split ->
                RealGroupAggregates.group(RealGroupSelector(null, split, taskClass), manifest, records, validation)
            }
    }

    fun write(manifest: RealRepositoryManifest, records: List<RealRepositoryMeasurement>, output: Path): Boolean {
        val report = aggregate(manifest, records)
        Files.createDirectories(output)
        Files.writeString(output.resolve("real-repositories.json"), json.encodeToString(report) + "\n")
        Files.writeString(output.resolve("real-repositories.md"), RealReportMarkdown.render(report))
        return report.tier1Passed
    }

    private fun exclusions(
        manifest: RealRepositoryManifest,
        repositories: List<String>,
    ): Map<String, Map<String, Int>> = repositories.associateWith { repository ->
        manifest.exclusions[repository].orEmpty().entries.groupBy { (reason, _) ->
            reason.takeIf { it in CorpusExclusions.reasons } ?: "other"
        }.toSortedMap().mapValues { (_, values) -> values.sumOf { it.value } }
    }

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
    }
}
