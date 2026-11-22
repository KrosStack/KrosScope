package io.github.krosstack.kscope.benchmark.semantic.cost

import io.github.krosstack.kscope.benchmark.context.ContextAnalysisIdentityTest.Fixture
import io.github.krosstack.kscope.benchmark.context.CountingEstimator
import io.github.krosstack.kscope.benchmark.context.identity.ContextPipelineIdentity
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SemanticCostProbeTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `warmups never enter measured percentiles`() = Fixture(root).use { fixture ->
        val run = run(fixture)
        val result = run.first.run()
        assertEquals(18, fixture.fake.queriesEmbedded - run.second.queriesBefore)
        assertEquals(24, result.samples.size)
        assertEquals(6, result.summaries.size)
        assertTrue(result.summaries.all { it.count == 3 && it.p50Nanos == 10L && it.p95Nanos == 10L })
        assertEquals(1, run.second.closed)
    }

    @Test
    fun `cold and warm samples retain their boundary and identity`() = Fixture(root).use { fixture ->
        val result = run(fixture).first.run()
        assertEquals(6, result.samples.count { it.boundary == SemanticCostProbe.Boundary.SERVICE_COLD })
        assertEquals(18, result.samples.count { it.boundary == SemanticCostProbe.Boundary.IDLE })
        assertTrue(result.samples.all { it.pipeline == fixture.pipeline })
        assertTrue(result.samples.filter { it.ml == MlMode.ON }.all { it.semantic.state == SemanticState.USED })
        assertTrue(result.notRun.any { it.contains("one-shot") })
    }

    @Test
    fun `eleven second USED calls enter successful warm semantic percentiles`() = Fixture(root).use { f ->
        val result = run(f, tick = 11_000_000_000).first.run()
        assertEquals(24, result.samples.size)
        assertTrue(result.samples.filter { it.ml == MlMode.ON }.all { it.accepted })
        assertTrue(
            result.summaries.filter { it.ml == MlMode.ON }.all {
                it.count == 3 && it.rejected == 0 && it.p50Nanos == 11_000_000_000 && it.p95Nanos == 11_000_000_000
            },
        )
    }

    @Test
    fun `a negative API duration fails the probe and names the broken clock`() = Fixture(root).use { f ->
        val (probe, recording) = run(f, tick = -10)
        val failure = assertFailsWith<IllegalStateException> { probe.run() }
        assertTrue(failure.message.orEmpty().contains("clock", ignoreCase = true))
        assertTrue(failure.message.orEmpty().contains("negative", ignoreCase = true))
        assertEquals(1, recording.closed)
    }

    @Test
    fun `fallback requests are not recorded as successful warm semantic samples`() = Fixture(root).use { f ->
        Files.delete(f.root.resolve(".kscope/embeddings.json"))
        val fallback = run(f).first.run()
        assertTrue(
            fallback.samples.filter { it.ml == MlMode.ON }.all {
                !it.accepted && it.rejection == SemanticCostProbe.Rejection.SEMANTIC
            },
        )
        assertTrue(fallback.summaries.filter { it.ml == MlMode.ON }.all { it.count == 0 })
    }

    @Test
    fun `changed pipeline identity cannot contribute successful warm samples`() = Fixture(root).use { f ->
        var reads = 0
        val (probe, _) = run(f, identity = {
            reads++
            if (reads < 8) f.pipeline else f.pipeline.copy(generation = Fixture.hash("changed"))
        })
        val result = probe.run()
        assertTrue(result.samples.filter { it.boundary == SemanticCostProbe.Boundary.IDLE }.all { !it.accepted })
        assertTrue(result.samples.any { it.pipeline?.generation == Fixture.hash("changed") })
    }

    @Test
    fun `a requested active workload without a pending job stays not run`() = Fixture(root).use { f ->
        val (base, _) = run(f, active = true)
        val result = base.run()
        assertTrue(result.notRun.any { it.contains("Active-indexing workload") })
        assertTrue(result.samples.none { it.boundary == SemanticCostProbe.Boundary.ACTIVE_INDEXING })
    }

    @Test
    fun `active indexing samples keep client wait separate from API time`() = Fixture(root).use { f ->
        val result = run(f, active = true, pending = true).first.run()
        val active = result.samples.filter { it.boundary == SemanticCostProbe.Boundary.ACTIVE_INDEXING }
        assertEquals(18, active.size)
        assertTrue(active.all { it.clientNanos > it.nanos && it.nanos == 10L && it.pipeline == f.pipeline })
        assertTrue(
            result.summaries.filter { it.boundary == SemanticCostProbe.Boundary.ACTIVE_INDEXING }
                .all { it.count == 3 && it.p95Nanos == 10L },
        )
    }

    @Test
    fun `resident sampling closes on failure and unreadable memory stays null`() = Fixture(root).use { f ->
        var closed = 0
        val memory = ProcessResidentMemory(readBytes = { emptyMap() }, schedule = {
            it()
            AutoCloseable { closed++ }
        })
        val probe = SemanticCostProbe(
            options(f),
            SemanticCostProbe.Dependencies(
                open = { _, _ -> error("service open failed") },
                now = { 0L },
                memory = memory,
            ),
        )
        assertFailsWith<IllegalStateException> { probe.run() }
        assertEquals(1, closed)
        val successful = run(f).first.run()
        assertTrue(successful.memory.isNotEmpty())
        assertTrue(successful.memory.all { it.bytes == null })
    }

    private fun options(f: Fixture) = SemanticCostOptions(
        f.root,
        f.root.resolve(".kscope"),
        root.resolve("out"),
        f.pipeline,
        mapOf(
            "reference" to "windows",
            "cpu" to "test",
            "cores" to "4",
            "ram" to "test",
            "os" to "test",
            "jvm" to "test",
        ),
        SemanticCostOptions.Workload(Fixture.hash("queries"), listOf("Change anchor text", "Find other text")),
    )

    private fun run(
        f: Fixture,
        tick: Long = 10,
        identity: () -> ContextPipelineIdentity = { f.pipeline },
        active: Boolean = false,
        pending: Boolean = false,
    ): Pair<SemanticCostProbe, Recording> {
        val recording = Recording(f.fake.queriesEmbedded)
        var clock = 0L
        val memory = ProcessResidentMemory(readBytes = { emptyMap() }, schedule = {
            it()
            AutoCloseable { recording.closed++ }
        })
        val dependencies = SemanticCostProbe.Dependencies(
            open = { path, options -> f.fake.open(path, options.copy(semanticIndexing = SemanticIndexingMode.OFF)) },
            now = { clock.also { clock += tick } },
            readIdentity = { _, _ -> identity() },
            memory = memory,
            advance = {
                clock += 100
                pending
            },
        )
        val probe = SemanticCostProbe(
            options(f).copy(workload = options(f).workload?.copy(activeIndexing = active)),
            dependencies,
            SemanticCostProbe.Sampling(warmups = 2, repetitions = 3, estimator = CountingEstimator()),
        )
        return probe to recording
    }

    private class Recording(val queriesBefore: Int, var closed: Int = 0)
}
