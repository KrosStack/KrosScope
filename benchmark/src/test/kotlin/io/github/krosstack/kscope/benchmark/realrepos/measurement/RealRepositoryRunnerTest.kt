package io.github.krosstack.kscope.benchmark.realrepos.measurement

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkOptions
import io.github.krosstack.kscope.benchmark.localization.BenchmarkMethod
import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealMeasurementStatus
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryMeasurement
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.report.RealRepositoryReports
import io.github.krosstack.kscope.model.context.TokenCount
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealRepositoryRunnerTest {
    @TempDir
    lateinit var output: Path

    @Test
    fun `all methods score parent content before child incremental and separate verification`() {
        CorpusRepository("sample").use { repo ->
            val parent = repo.commit("Initial", mapOf("README.md" to PARENT_TEXT))
            val child = repo.commit("Correct anchors", mapOf("README.md" to CHILD_TEXT, "NEXT.md" to "next\n"))
            val task = task(parent, child, verify = true)
            val result = run(repo, listOf(task)).single()

            assertEquals(RealMeasurementStatus.COMPLETE, result.status)
            assertEquals(BenchmarkMethod.entries.toSet(), assertNotNull(result.localization).methods.keys)
            val contexts = assertNotNull(result.context).budgets
            assertEquals(listOf(2_000, 4_000, 8_000, 16_000), contexts.map { it.budget.value })
            contexts.forEach { budget ->
                val methods = budget.tasks.single().methods
                assertEquals(ContextBenchmarkOptions.DEFAULT_METHODS.toSet(), methods.keys)
                val text = methods.getValue(ContextBenchmarkMethod.BM25_RAW).context.fragments.single().text
                assertEquals(PARENT_TEXT, text)
            }
            assertEquals(1, assertNotNull(result.scan).artifacts)
            assertEquals(2, assertNotNull(result.update).artifacts)
            assertEquals(true, assertNotNull(result.verification).verification?.matches)
            assertNotNull(result.timings.incrementalMillis)
            assertNotNull(result.timings.verificationMillis)
            assertEquals(child, repo.spec().head)
            assertEquals(CHILD_TEXT, Files.readString(repo.root.resolve("README.md")))
            assertFalse(Files.exists(repo.root.resolve(".kscope")))
        }
    }

    @Test
    fun `a budget subset is scored reported and bound to the run directory`() {
        CorpusRepository("sample").use { repo ->
            val parent = repo.commit("Initial", mapOf("README.md" to PARENT_TEXT))
            val child = repo.commit("Correct anchors", mapOf("README.md" to CHILD_TEXT))
            val manifest = manifest(repo, listOf(task(parent, child)))
            val runner = RealRepositoryRunner()
            val subset = RealRunOptions(output, IDENTITY, budgets = listOf(TokenCount(2_000), TokenCount(4_000)))

            val records = runner.run(manifest, subset)

            val result = records.single()
            assertEquals(RealMeasurementStatus.COMPLETE, result.status)
            assertEquals(listOf(2_000, 4_000), assertNotNull(result.context).budgets.map { it.budget.value })
            val report = RealRepositoryReports.aggregate(manifest, records)
            assertEquals(listOf(2_000, 4_000), report.budgets.map { it.value })
            assertTrue(report.groups.filter { it.expected > 0 }.all { it.valid && it.measured == it.expected })
            assertTrue(report.groups.all { group -> group.context.map { it.budget.value } == listOf(2_000, 4_000) })
            assertFalse(report.tier1Passed)
            assertEquals(records, runner.run(manifest, subset))
            assertFailsWith<IllegalArgumentException> { runner.run(manifest, RealRunOptions(output, IDENTITY)) }
        }
    }

    @Test
    fun `completed records resume without accessing checkout or scratch`() {
        CorpusRepository("sample").use { repo ->
            val parent = repo.commit("Initial", mapOf("README.md" to PARENT_TEXT))
            val child = repo.commit("Correct anchors", mapOf("README.md" to CHILD_TEXT))
            val manifest = manifest(repo, listOf(task(parent, child)))
            val runner = RealRepositoryRunner()
            val options = RealRunOptions(output, IDENTITY)
            val first = runner.run(manifest, options)
            val git = repo.root.resolve(".git")
            val hidden = repo.root.resolve(".git-paused")
            val scratch = output.resolve("scratch")
            val hiddenScratch = output.resolve("scratch-paused")
            Files.move(git, hidden)
            Files.move(scratch, hiddenScratch)
            try {
                assertEquals(first, runner.run(manifest, options))
                assertEquals(1, first.size)
            } finally {
                Files.move(hiddenScratch, scratch)
                Files.move(hidden, git)
            }
        }
    }

    @Test
    fun `resume rejects another freeze identity or changed task metadata`() {
        CorpusRepository("sample").use { repo ->
            val parent = repo.commit("Initial", mapOf("README.md" to PARENT_TEXT))
            val child = repo.commit("Correct anchors", mapOf("README.md" to CHILD_TEXT))
            val task = task(parent, child)
            val manifest = manifest(repo, listOf(task))
            val runner = RealRepositoryRunner()
            runner.run(manifest, RealRunOptions(output, IDENTITY))

            assertFailsWith<IllegalArgumentException> {
                runner.run(manifest, RealRunOptions(output, "b".repeat(64)))
            }
            assertFailsWith<IllegalArgumentException> {
                runner.run(
                    manifest.copy(tasks = listOf(task.copy(statement = "Changed task"))),
                    RealRunOptions(output, IDENTITY),
                )
            }
        }
    }

    @Test
    fun `failed tasks retain phase failure and later tasks reuse one checkout and index`() {
        CorpusRepository("sample").use { repo ->
            val parent = repo.commit("Initial", mapOf("README.md" to PARENT_TEXT))
            val child = repo.commit("Correct anchors", mapOf("README.md" to CHILD_TEXT))
            val failed = task("0".repeat(40), child).copy(id = "sample:missing")
            val result = run(repo, listOf(failed, task(parent, child)))

            assertEquals(listOf(RealMeasurementStatus.FAILED, RealMeasurementStatus.COMPLETE), result.map { it.status })
            assertTrue(assertNotNull(result.first().failure).contains("parent"))
            assertEquals(1L, Files.list(output.resolve("scratch")).use { it.count() })
            assertEquals(1L, Files.list(output.resolve("indexes")).use { it.count() })
            assertEquals(2L, Files.list(output.resolve("tasks")).use { it.count() })
        }
    }

    @Test
    fun `unsafe IDs and output inside a user checkout are rejected before mutation`() {
        CorpusRepository("sample").use { repo ->
            val parent = repo.commit("Initial", mapOf("README.md" to PARENT_TEXT))
            val child = repo.commit("Correct anchors", mapOf("README.md" to CHILD_TEXT))
            val manifest = manifest(repo, listOf(task(parent, child)))
            val runner = RealRepositoryRunner()
            assertFailsWith<IllegalArgumentException> {
                runner.run(manifest, RealRunOptions(repo.root.resolve("reports"), IDENTITY))
            }
            assertFailsWith<IllegalArgumentException> {
                runner.run(
                    manifest.copy(tasks = listOf(manifest.tasks.single().copy(id = "../escape"))),
                    RealRunOptions(output, IDENTITY),
                )
            }
            assertFalse(Files.exists(repo.root.resolve("reports")))
        }
    }

    private fun run(repo: CorpusRepository, tasks: List<RealCommitTask>): List<RealRepositoryMeasurement> =
        RealRepositoryRunner().run(manifest(repo, tasks), RealRunOptions(output, IDENTITY))

    private fun manifest(repo: CorpusRepository, tasks: List<RealCommitTask>): RealRepositoryManifest =
        RealRepositoryManifest(listOf(repo.spec()), tasks, emptyMap(), "Synthetic parent-only protocol")

    private fun task(parent: String, child: String, verify: Boolean = false): RealCommitTask = RealCommitTask(
        "sample:$child", "sample", parent, child, 1, "parent anchors", listOf("README.md"), RealSplit.DEV, verify,
    )

    private companion object {
        const val PARENT_TEXT = "parent anchors are visible only before the change\n"
        const val CHILD_TEXT = "child implementation replaces previous content\n"
        val IDENTITY = "a".repeat(64)
    }
}
