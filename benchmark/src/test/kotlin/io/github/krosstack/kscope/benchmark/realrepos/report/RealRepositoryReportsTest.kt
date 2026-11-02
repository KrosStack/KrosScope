package io.github.krosstack.kscope.benchmark.realrepos.report

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkOptions
import io.github.krosstack.kscope.benchmark.context.ContextGoldSpec
import io.github.krosstack.kscope.benchmark.context.ContextSourceIssue
import io.github.krosstack.kscope.benchmark.context.ContextSourceIssueReason
import io.github.krosstack.kscope.benchmark.localization.BenchmarkMethod
import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealIdentifier
import io.github.krosstack.kscope.benchmark.realrepos.RealMeasurementStatus
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryMeasurement
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.corpus.CorpusExclusions
import io.github.krosstack.kscope.benchmark.stats.ComparisonMetric
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RealRepositoryReportsTest {
    @Test
    fun `repository and pooled split means weight commits equally and keep dev separate from test`() {
        val report = RealRepositoryReports.aggregate(RealReportFixture.manifest, RealReportFixture.measurements())
        val repository = report.groups.single { it.repository == "a" && it.split == RealSplit.DEV }
        val pooled = report.groups.single { it.repository == null && it.split == RealSplit.DEV }

        assertTrue(report.complete)
        assertTrue(report.valid)
        assertTrue(report.tier1Passed)
        assertEquals(0.5, repository.localization.methods.getValue(BenchmarkMethod.FULL)?.recall5)
        assertEquals(2.0 / 3.0, pooled.localization.methods.getValue(BenchmarkMethod.FULL)?.recall5)
        assertEquals(
            0.4,
            report.groups.single { it.repository == null && it.split == RealSplit.TEST }
                .localization.methods.getValue(BenchmarkMethod.FULL)?.recall5,
        )
        val paired = pooled.localization.tier2Comparisons.single { it.metric == ComparisonMetric.RECALL_5 }
        assertEquals(3, paired.pairs.size)
        assertEquals(2.0 / 3.0 - 0.1, paired.pointDelta ?: error("Missing CI delta"), 1e-12)
        assertEquals(ContextBenchmarkOptions.DEFAULT_METHODS.toSet(), pooled.context.first().methods.keys)
        assertEquals(BenchmarkMethod.entries.toSet(), pooled.localization.methods.keys)
    }

    @Test
    fun `missing and failed records retain partial counts and cannot pass`() {
        val records = RealReportFixture.measurements()
        val missing = RealRepositoryReports.aggregate(RealReportFixture.manifest, records.dropLast(1))
        val failed = RealRepositoryReports.aggregate(
            RealReportFixture.manifest,
            records.dropLast(1) + records.last().copy(
                status = RealMeasurementStatus.FAILED,
                failure = "context:${RealReportFixture.PRIVATE}",
            ),
        )

        assertFalse(missing.complete)
        assertFalse(missing.tier1Passed)
        assertEquals(1, missing.groups.single { it.repository == null && it.split == RealSplit.TEST }.missing)
        assertFalse(failed.valid)
        assertFalse(failed.tier1Passed)
        assertEquals(1, failed.groups.single { it.repository == null && it.split == RealSplit.TEST }.failed)
    }

    @Test
    fun `an incomplete test group does not poison a complete independent dev group`() {
        val records = RealReportFixture.measurements().dropLast(1)
        val report = RealRepositoryReports.aggregate(RealReportFixture.manifest, records)
        val dev = report.groups.single { it.repository == "a" && it.split == RealSplit.DEV }
        val test = report.groups.single { it.repository == "b" && it.split == RealSplit.TEST }

        assertTrue(dev.complete)
        assertTrue(dev.valid)
        assertTrue(dev.tier1Passed)
        assertFalse(test.complete)
        assertFalse(report.complete)
        assertFalse(report.tier1Passed)
    }

    @Test
    fun `mixed identities duplicates and altered frozen tasks are rejected`() {
        val records = RealReportFixture.measurements()
        val invalid = listOf(
            records + records.first(),
            records.dropLast(1) + records.last().copy(identity = "other"),
            records.dropLast(1) + records.last().copy(task = records.last().task.copy(parent = "altered")),
        )

        invalid.forEach {
            val report = RealRepositoryReports.aggregate(RealReportFixture.manifest, it)
            assertFalse(report.valid)
            assertFalse(report.tier1Passed)
            assertTrue(report.validationIssues.isNotEmpty())
        }
    }

    @Test
    fun `complete status cannot hide missing update scan timing or an unsupported record version`() {
        val records = RealReportFixture.measurements()
        val first = records.first()
        val invalid = listOf(
            first.copy(update = null),
            first.copy(scan = null),
            first.copy(timings = first.timings.copy(fullScanMillis = null)),
            first.copy(timings = first.timings.copy(incrementalMillis = null)),
            first.copy(schemaVersion = 2),
        )

        invalid.forEach { changed ->
            val report = RealRepositoryReports.aggregate(RealReportFixture.manifest, listOf(changed) + records.drop(1))
            assertFalse(report.valid)
            assertFalse(report.tier1Passed)
        }
    }

    @Test
    fun `invalid source accounting makes a complete record fail instead of disappearing from the report`() {
        val records = RealReportFixture.measurements()
        val original = records.first()
        val context = original.context ?: error("Missing test context")
        val budget = context.budgets.first()
        val task = budget.tasks.single()
        val method = task.methods.getValue(ContextBenchmarkMethod.FULL_COMPILER)
        val invalid = method.copy(
            scores = method.scores.copy(
                sourceIssues = listOf(
                    ContextSourceIssue(0, ContextSourceIssueReason.TEXT_MISMATCH),
                ),
            ),
        )
        val changedTask = task.copy(methods = task.methods + (ContextBenchmarkMethod.FULL_COMPILER to invalid))
        val changed = original.copy(
            context = context.copy(
                budgets = listOf(budget.copy(tasks = listOf(changedTask))) +
                    context.budgets.drop(1),
            ),
        )
        val report = RealRepositoryReports.aggregate(RealReportFixture.manifest, listOf(changed) + records.drop(1))

        assertFalse(report.valid)
        assertFalse(report.tier1Passed)
        assertTrue(report.validationIssues.containsKey(RealReportIssue.INVALID_SOURCE_ACCOUNTING))
    }

    @Test
    fun `public files whitelist numeric aggregates and contain no private record strings`() {
        val directory = Files.createTempDirectory("kscope-real-report")
        try {
            assertTrue(
                RealRepositoryReports.write(RealReportFixture.manifest, RealReportFixture.measurements(), directory),
            )
            val json = Files.readString(directory.resolve("real-repositories.json"))
            val markdown = Files.readString(directory.resolve("real-repositories.md"))

            assertFalse(json.contains(RealReportFixture.PRIVATE))
            assertFalse(markdown.contains(RealReportFixture.PRIVATE))
            assertFalse(json.contains("C:/"))
            assertFalse(json.contains("statement"))
            assertFalse(json.contains("markdown"))
            assertFalse(json.contains("goldFiles"))
            assertEquals("1", Json.parseToJsonElement(json).jsonObject.getValue("schemaVersion").toString())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `gold absent from the index keeps the full gold denominator and is counted per group`() {
        val absent = "absent"
        val tasks = RealReportFixture.tasks.mapIndexed { index, task ->
            if (index == 0) task.copy(goldFiles = listOf(RealReportFixture.PRIVATE, absent)) else task
        }
        val records = RealReportFixture.measurements().mapIndexed { index, record ->
            if (index == 0) withAbsentGold(record, tasks[0], absent) else record
        }
        val report = RealRepositoryReports.aggregate(RealReportFixture.manifest.copy(tasks = tasks), records)
        val repository = report.groups.single { it.repository == "a" && it.split == RealSplit.DEV }
        val other = report.groups.single { it.repository == "b" && it.split == RealSplit.DEV }

        assertTrue(report.valid)
        assertEquals(RealAbsentGold(tasks = 1, files = 1), repository.goldAbsentFromIndex)
        assertEquals(RealAbsentGold(tasks = 0, files = 0), other.goldAbsentFromIndex)
        val full = repository.context.first().methods.getValue(ContextBenchmarkMethod.FULL_COMPILER)
        assertEquals((0.2 * 0.5 + 0.8) / 2, full?.goldContextRecall ?: error("Missing mean"), 1e-12)
        assertEquals((0.2 * 0.5 + 0.8) / 2, full.fileRecall, 1e-12)
        assertEquals((0.2 + 0.8) / 2, full.tokenPrecision, 1e-12)
    }

    @Test
    fun `eligibility exclusion reasons emitted by the corpus keep their names`() {
        val reasons = CorpusExclusions.reasons.associateWith { 1 }
        val manifest = RealReportFixture.manifest.copy(exclusions = mapOf("a" to reasons + ("unlisted" to 2)))
        val report = RealRepositoryReports.aggregate(manifest, RealReportFixture.measurements())

        assertEquals(reasons + ("other" to 2), report.eligibilityExclusions.getValue("a"))
    }

    @Test
    fun `a repository id with a dot, which a run accepts, is reported like any other`() {
        val dotted = "kotlinx.coroutines"
        fun renamed(task: RealCommitTask) = if (task.repository == "a") task.copy(repository = dotted) else task
        val original = RealReportFixture.manifest
        val manifest = original.copy(
            repositories = original.repositories.map { if (it.id == "a") it.copy(id = dotted) else it },
            tasks = original.tasks.map(::renamed),
            exclusions = emptyMap(),
        )
        val records = RealReportFixture.measurements().map { it.copy(task = renamed(it.task)) }

        val report = RealRepositoryReports.aggregate(manifest, records)

        assertTrue(RealIdentifier.isValid(dotted))
        assertTrue(report.valid)
        assertTrue(report.tier1Passed)
        assertTrue(report.groups.any { it.repository == dotted })
    }

    private fun withAbsentGold(
        record: RealRepositoryMeasurement,
        task: RealCommitTask,
        absent: String,
    ): RealRepositoryMeasurement {
        val context = record.context ?: error("Missing test context")
        val budgets = context.budgets.map { budget ->
            val changed = budget.tasks.single().copy(
                gold = ContextGoldSpec(task.goldFiles),
                excludedMissingFiles = listOf(absent),
                resolvedGoldFiles = listOf(RealReportFixture.PRIVATE),
            )
            budget.copy(tasks = listOf(changed))
        }
        return record.copy(task = task, context = context.copy(budgets = budgets))
    }
}
