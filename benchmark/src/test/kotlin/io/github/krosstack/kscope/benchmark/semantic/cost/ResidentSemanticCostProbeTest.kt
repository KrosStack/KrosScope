package io.github.krosstack.kscope.benchmark.semantic.cost

import io.github.krosstack.kscope.benchmark.context.ContextAnalysisIdentityTest.Fixture
import io.github.krosstack.kscope.benchmark.context.CountingEstimator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResidentSemanticCostProbeTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `one resident round reads all distinct process ids in one batch`() {
        val calls = mutableListOf<List<Long>>()
        val memory = ProcessResidentMemory(readBytes = { pids ->
            calls.add(pids)
            mapOf(11L to 4096L, 22L to 0L)
        }, schedule = {
            it()
            AutoCloseable {}
        })
        memory.Sampler({ listOf(22L, 11L, 22L, 33L) }, { 17L }).use { sampler ->
            assertEquals(listOf(listOf(11L, 22L, 33L)), calls)
            assertEquals(
                listOf(
                    ProcessResidentMemory.Reading(11L, 4096L, 17L),
                    ProcessResidentMemory.Reading(22L, null, 17L),
                    ProcessResidentMemory.Reading(33L, null, 17L),
                ),
                sampler.snapshot(),
            )
        }
    }

    @Test
    fun `the probe reports the resident round cap with its timestamp`() = Fixture(root).use { fixture ->
        var clock = 0L
        val memory = ProcessResidentMemory(readBytes = { emptyMap() }, schedule = {
            repeat(1801) { _ -> it() }
            AutoCloseable {}
        })
        val result = SemanticCostProbe(
            SemanticCostOptions(
                fixture.root,
                fixture.root.resolve(".kscope"),
                root.resolve("out"),
                fixture.pipeline,
                machine = mapOf(
                    "reference" to "windows",
                    "cpu" to "test",
                    "cores" to "4",
                    "ram" to "test",
                    "os" to "test",
                    "jvm" to "test",
                ),
                workload = SemanticCostOptions.Workload(Fixture.hash("queries"), listOf("Change anchor text")),
            ),
            SemanticCostProbe.Dependencies(
                open = { path, options -> fixture.fake.open(path, options) },
                now = { clock++ },
                readIdentity = { _, _ -> fixture.pipeline },
                memory = memory,
            ),
            SemanticCostProbe.Sampling(warmups = 0, repetitions = 1, estimator = CountingEstimator()),
        ).run()
        assertEquals(1800, result.memory.map { it.atNanos }.distinct().size)
        assertEquals(1799L, result.memory.last().atNanos)
        assertTrue(result.notRun.any { it.contains("MAX_SAMPLES=1800") && it.contains("1799 ns") })
        assertTrue(result.samples.isNotEmpty())
    }
}
