package io.github.krosstack.kscope.benchmark.semantic.cost

import io.github.krosstack.kscope.benchmark.context.ContextAnalysisIdentityTest.Fixture
import io.github.krosstack.kscope.benchmark.context.CountingEstimator
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import io.github.krosstack.kscope.service.semantic.advanceSemanticIndex
import io.github.krosstack.kscope.service.semantic.semanticNotice
import io.github.krosstack.kscope.service.semantic.semanticStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActiveSemanticCostProbeTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `failed deferred and completed first steps report distinct not run reasons`() {
        val lines = listOf("failed", "deferred", "complete").map { outcome ->
            Fixture(Files.createDirectories(root.resolve(outcome))).use { fixture ->
                var reason = "no pending index job"
                val result = probe(fixture) { scope ->
                    when (outcome) {
                        "failed" -> failFirstStep(fixture, scope)
                        "deferred" -> deferPublication(fixture, scope)
                        else -> finishIndexing(scope)
                    }
                    reason = scope.semanticNotice() ?: reason
                    false
                }.run()
                assertFalse(result.samples.any { it.boundary == SemanticCostProbe.Boundary.ACTIVE_INDEXING })
                result.notRun.single { it.startsWith("Active-indexing workload:") }.also {
                    assertEquals("Active-indexing workload: $reason, not run.", it)
                }
            }
        }
        assertEquals(3, lines.distinct().size)
        assertTrue(lines[0].contains("failed"))
        assertTrue(lines[1].contains("publication is deferred"))
        assertTrue(lines[2].contains("no pending index job"))
    }

    private fun failFirstStep(fixture: Fixture, scope: KrosScope) {
        val lock = fixture.root.resolve(".kscope/embeddings.lock")
        Files.deleteIfExists(lock)
        Files.createDirectory(lock)
        try {
            assertFalse(scope.advanceSemanticIndex())
            assertEquals(SemanticState.WORKER_UNAVAILABLE, scope.semanticStatus().state)
            assertTrue(scope.semanticNotice().orEmpty().contains("failed"))
        } finally {
            Files.delete(lock)
        }
    }

    private fun deferPublication(fixture: Fixture, scope: KrosScope) {
        val index = fixture.root.resolve(".kscope")
        val leases = Files.createDirectories(index.resolve("embeddings-leases"))
        for (digest in listOf('a', 'b', 'c')) {
            val name = "embeddings-${fixture.pipeline.provider}-${digest.toString().repeat(64)}.bin"
            Files.write(index.resolve(name), byteArrayOf())
            Files.write(leases.resolve("$name-held"), byteArrayOf())
        }
        finishIndexing(scope)
        assertTrue(scope.semanticNotice().orEmpty().contains("publication is deferred"))
    }

    private fun finishIndexing(scope: KrosScope) {
        repeat(100) {
            if (!scope.advanceSemanticIndex()) return
        }
        error("Indexing did not reach a terminal or deferred step")
    }

    private fun probe(fixture: Fixture, advance: (KrosScope) -> Boolean): SemanticCostProbe {
        var clock = 0L
        val options = SemanticCostOptions(
            fixture.root,
            fixture.root.resolve(".kscope"),
            root.resolve("out"),
            fixture.pipeline,
            mapOf("reference" to "windows"),
            SemanticCostOptions.Workload(Fixture.hash("queries"), listOf("Change anchor text"), activeIndexing = true),
        )
        val dependencies = SemanticCostProbe.Dependencies(
            open = { path, open ->
                fixture.fake.open(path, open.copy(semanticIndexing = SemanticIndexingMode.DEFERRED))
            },
            now = { clock++ },
            readIdentity = { _, _ -> fixture.pipeline },
            memory = ProcessResidentMemory(readBytes = { emptyMap() }, schedule = { AutoCloseable {} }),
            advance = advance,
        )
        return SemanticCostProbe(
            options,
            dependencies,
            SemanticCostProbe.Sampling(warmups = 0, repetitions = 1, estimator = CountingEstimator()),
        )
    }
}
