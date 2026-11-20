package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.context.identity.ContextBenchmarkIdentity
import io.github.krosstack.kscope.benchmark.context.identity.ContextBenchmarkSelection
import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ContextPipelineIdentityTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `structural tail policy cannot resume a head only checkpoint`() {
        ContextAnalysisIdentityTest.Fixture(directory).use { fixture ->
            val old = fixture.pipeline
            val current = old.copy(policy = old.policy + ("context-input" to "fused-head-structural-tail-1"))
            val methods = ContextBenchmarkOptions.SEMANTIC_METHODS
            val previous = ContextBenchmarkSelection(methods, old)
            val selection = ContextBenchmarkSelection(methods, current)
            val request = ContextCheckpointRequest(BenchmarkSet.ORIGINAL, fixture.task.id, "same-input")
            val results = ContextBenchmark.evaluate(
                fixture.scope,
                fixture.root,
                listOf(fixture.task),
                ContextBenchmarkOptions(estimator = CountingEstimator()),
            ).budgets.associate { it.budget to it.tasks.single() }
            val path = directory.resolve("checkpoints")
            val store = FileContextTaskCheckpoints(path, "same-freeze", analysisKeys = previous.analysisKeys)
            store.save(request, results)
            assertEquals(results, store.load(request))
            val updated = FileContextTaskCheckpoints(path, "same-freeze", analysisKeys = selection.analysisKeys)
            assertNull(updated.load(request))
            assertNotEquals(old.fingerprint(), current.fingerprint())
            assertNotEquals(
                ContextBenchmarkIdentity.task("snapshot", fixture.task, ContextBenchmarkOptions(selection = previous)),
                ContextBenchmarkIdentity.task("snapshot", fixture.task, ContextBenchmarkOptions(selection = selection)),
            )
        }
    }
}
