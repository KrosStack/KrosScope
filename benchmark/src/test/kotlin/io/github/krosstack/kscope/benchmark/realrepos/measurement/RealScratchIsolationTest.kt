package io.github.krosstack.kscope.benchmark.realrepos.measurement

import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealMeasurementStatus
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.corpus.CorpusGit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealScratchIsolationTest {
    @TempDir
    lateinit var output: Path

    @Test
    fun `pending task rejects ignored scratch contamination and preserves its contents`() {
        CorpusRepository("sample").use { repo ->
            val parent = repo.commit("Initial", mapOf("README.md" to "initial\n", ".gitignore" to "ignored.tmp\n"))
            val first = repo.commit("Correct first", mapOf("README.md" to "first anchor\n"))
            val second = repo.commit("Correct second", mapOf("README.md" to "second anchor\n"))
            val tasks = listOf(
                task(parent, first, RealSplit.DEV),
                task(first, second, RealSplit.TEST),
            )
            val manifest = RealRepositoryManifest(listOf(repo.spec()), tasks, emptyMap(), "Synthetic protocol")
            val runner = RealRepositoryRunner()
            val identity = "a".repeat(64)
            assertEquals(
                RealMeasurementStatus.COMPLETE,
                runner.run(manifest, RealRunOptions(output, identity, RealSplit.DEV)).single().status,
            )
            val scratch = Files.list(output.resolve("scratch")).use { it.findFirst().orElseThrow() }
            val unexpected = scratch.resolve("ignored.tmp")
            Files.writeString(unexpected, "preserve these bytes\n")

            val record = runner.run(manifest, RealRunOptions(output, identity, RealSplit.TEST)).single()
            assertEquals(RealMeasurementStatus.FAILED, record.status)
            assertTrue(record.failure.orEmpty().contains("Unexpected scratch"))
            assertEquals("preserve these bytes\n", Files.readString(unexpected))
            assertEquals(first, CorpusGit(scratch).read("rev-parse", "HEAD").trim())
        }
    }

    private fun task(parent: String, child: String, split: RealSplit): RealCommitTask = RealCommitTask(
        "sample:$child",
        "sample",
        parent,
        child,
        1,
        "anchor",
        listOf("README.md"),
        split,
    )
}
