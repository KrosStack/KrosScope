package io.github.krosstack.kscope.benchmark.realrepos.measurement

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.localization.BenchmarkMethod
import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealMeasurementStatus
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryMeasurement
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositorySpec
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.report.RealAbsentGold
import io.github.krosstack.kscope.benchmark.realrepos.report.RealRepositoryReports
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealRepositoryRobustnessTest {
    @TempDir
    lateinit var output: Path

    @Test
    fun `gold absent from the parent index counts as a miss and does not fail the task`() {
        CorpusRepository("sample").use { repo ->
            val parent = repo.commit("Initial", mapOf("README.md" to PARENT_TEXT))
            val child = repo.commit("Correct anchors", mapOf("README.md" to CHILD_TEXT))
            val task = task(parent, child).copy(goldFiles = listOf("Absent.kt", "README.md"))
            val result = run(repo, listOf(task)).single()

            assertEquals(RealMeasurementStatus.COMPLETE, result.status)
            val localization = assertNotNull(result.localization).tasks.single()
            assertEquals(listOf("Absent.kt"), localization.missingGoldFiles)
            assertEquals(0.5, localization.methods.getValue(BenchmarkMethod.PATH_ORDER).scores.recall10)
            assertNotNull(result.context).budgets.forEach { budget ->
                assertEquals(listOf("Absent.kt"), budget.tasks.single().excludedMissingFiles)
            }
            val report = RealRepositoryReports.aggregate(manifest(repo, listOf(task)), listOf(result))
            val group = report.groups.single { it.repository == null && it.split == RealSplit.DEV }
            assertTrue(group.complete)
            assertTrue(group.valid)
            assertEquals(RealAbsentGold(tasks = 1, files = 1), group.goldAbsentFromIndex)
            assertEquals(0.5, group.localization.methods.getValue(BenchmarkMethod.PATH_ORDER)?.recall10)
            val context = group.context.first().methods.getValue(ContextBenchmarkMethod.PATH_ORDER)
            assertEquals(0.5, context?.goldContextRecall)
        }
    }

    @Test
    fun `a task whose gold is entirely absent from the index still completes with zero recall`() {
        CorpusRepository("sample").use { repo ->
            val parent = repo.commit("Initial", mapOf("README.md" to PARENT_TEXT))
            val child = repo.commit("Correct anchors", mapOf("README.md" to CHILD_TEXT))
            val task = task(parent, child).copy(goldFiles = listOf("Absent.kt"))
            val result = run(repo, listOf(task)).single()

            assertEquals(RealMeasurementStatus.COMPLETE, result.status)
            val report = RealRepositoryReports.aggregate(manifest(repo, listOf(task)), listOf(result))
            val group = report.groups.single { it.repository == null && it.split == RealSplit.DEV }
            assertTrue(group.complete)
            assertTrue(group.valid)
            assertEquals(0.0, group.localization.methods.getValue(BenchmarkMethod.FULL)?.recall10)
            val context = group.context.last().methods.getValue(ContextBenchmarkMethod.FULL_COMPILER)
            assertEquals(0.0, context?.goldContextRecall)
        }
    }

    @Test
    fun `an unexpected non fatal exception is recorded as a failed task with its reason`() {
        CorpusRepository("sample").use { repo ->
            val parent = repo.commit("Initial", mapOf("README.md" to PARENT_TEXT))
            val child = repo.commit("Correct anchors", mapOf("README.md" to CHILD_TEXT))
            val storage = object : RealRunStorage(output) {
                override fun checkpoints(task: String): Path = throw UnsupportedOperationException("synthetic")
            }
            val record = run(repo, task(parent, child), storage).execute()

            assertEquals(RealMeasurementStatus.FAILED, record.status)
            assertTrue(assertNotNull(record.failure).contains("UnsupportedOperationException: synthetic"))
            assertNotNull(record.scan)
            assertNotNull(record.timings.fullScanMillis)
        }
    }

    @Test
    fun `a repository filter runs only the tasks of that repository`() {
        CorpusRepository("sample").use { repo ->
            val parent = repo.commit("Initial", mapOf("README.md" to PARENT_TEXT))
            val child = repo.commit("Correct anchors", mapOf("README.md" to CHILD_TEXT))
            val other = RealRepositorySpec(
                "other",
                output.resolveSibling("${output.fileName}-absent").toString(),
                "c".repeat(40),
            )
            val foreign = task(parent, child).copy(id = "other:$child", repository = "other")
            val manifest = RealRepositoryManifest(
                listOf(other, repo.spec()),
                listOf(foreign, task(parent, child)),
                emptyMap(),
                "Synthetic protocol",
            )
            val results = RealRepositoryRunner().run(manifest, RealRunOptions(output, IDENTITY, repository = "sample"))

            assertEquals(listOf("sample:$child"), results.map { it.task.id })
            assertEquals(RealMeasurementStatus.COMPLETE, results.single().status)
            assertFailsWith<IllegalArgumentException> {
                RealRepositoryRunner().run(manifest, RealRunOptions(output, IDENTITY, repository = "unknown"))
            }
        }
    }

    @Test
    fun `fatal errors are not recorded as task failures`() {
        CorpusRepository("sample").use { repo ->
            val parent = repo.commit("Initial", mapOf("README.md" to PARENT_TEXT))
            val child = repo.commit("Correct anchors", mapOf("README.md" to CHILD_TEXT))
            val storage = object : RealRunStorage(output) {
                override fun checkpoints(task: String): Path = throw AssertionError("synthetic")
            }

            assertFailsWith<AssertionError> {
                run(repo, task(parent, child), storage).execute()
            }
        }
    }

    private fun run(repo: CorpusRepository, tasks: List<RealCommitTask>): List<RealRepositoryMeasurement> =
        RealRepositoryRunner().run(manifest(repo, tasks), RealRunOptions(output, IDENTITY))

    private fun manifest(repo: CorpusRepository, tasks: List<RealCommitTask>): RealRepositoryManifest =
        RealRepositoryManifest(listOf(repo.spec()), tasks, emptyMap(), "Synthetic parent-only protocol")

    private fun task(parent: String, child: String): RealCommitTask = RealCommitTask(
        "sample:$child",
        "sample",
        parent,
        child,
        1,
        "parent anchors",
        listOf("README.md"),
        RealSplit.DEV,
    )

    private fun run(repo: CorpusRepository, task: RealCommitTask, storage: RealRunStorage): RealTaskRun = RealTaskRun(
        RealWorkspaceSnapshot(mapOf(task.repository to repo.spec()), task, storage),
        task,
        IDENTITY,
        storage,
    )

    private companion object {
        const val PARENT_TEXT = "parent anchors are visible only before the change\n"
        const val CHILD_TEXT = "child implementation replaces previous content\n"
        val IDENTITY = "a".repeat(64)
    }
}
