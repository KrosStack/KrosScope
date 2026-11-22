package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.context.identity.ContextAnalysisKey
import io.github.krosstack.kscope.benchmark.context.identity.ContextBenchmarkSelection
import io.github.krosstack.kscope.benchmark.context.identity.ContextPipelineIdentity
import io.github.krosstack.kscope.benchmark.graph.WorkspaceNames
import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.benchmark.localization.LocalizationCorpus
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.RetrievalStage
import io.github.krosstack.kscope.spi.context.TokenEstimator
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContextPipelineCheckpointTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `changing only ML mode cannot load a completed OFF checkpoint`() {
        ContextAnalysisIdentityTest.Fixture(directory).use { fixture ->
            val off = ContextAnalysisKey(RetrievalStage.FULL, MlMode.OFF, null)
            val on = off.copy(ml = MlMode.ON, pipeline = fixture.pipeline.fingerprint())
            val request = ContextCheckpointRequest(BenchmarkSet.ORIGINAL, fixture.task.id, "same-input")
            val results = measure(fixture).budgets.associate { it.budget to it.tasks.single() }
            val store = store(listOf(off))
            store.save(request, results)
            assertEquals(results, store.load(request))
            assertNull(store(listOf(on)).load(request))
            assertNull(store(listOf(off.copy(stage = RetrievalStage.LEXICAL))).load(request))
        }
    }

    @Test
    fun `changing only provider or generation cannot load an old result`() {
        ContextAnalysisIdentityTest.Fixture(directory).use { fixture ->
            val request = ContextCheckpointRequest(BenchmarkSet.ORIGINAL, fixture.task.id, "same-input")
            val original = fixture.pipeline
            val variants = listOf(
                original.copy(provider = "other-provider"),
                original.copy(generation = ContextAnalysisIdentityTest.Fixture.hash("other-generation")),
            )
            val key = ContextAnalysisKey(RetrievalStage.FULL, MlMode.ON, original.fingerprint())
            store(listOf(key)).save(request, measure(fixture).budgets.associate { it.budget to it.tasks.single() })
            variants.forEach { assertNull(store(listOf(key.copy(pipeline = it.fingerprint()))).load(request)) }
            val events = mutableListOf<ContextProgressEvent>()
            measure(fixture, original, events)
            variants.forEach { changed ->
                events.clear()
                measure(fixture, changed, events)
                assertTrue(events.none { it.boundary == ContextProgressBoundary.CHECKPOINT_LOADED })
            }
        }
    }

    @Test
    fun `unchanged identity resumes all expected budgets`() {
        ContextAnalysisIdentityTest.Fixture(directory).use { fixture ->
            val baseline = measure(fixture)
            val events = mutableListOf<ContextProgressEvent>()
            val resumed = ContextBenchmark.evaluate(
                fixture.scope,
                fixture.root,
                listOf(fixture.task),
                ContextBenchmarkOptions(
                    estimator = CountingEstimator(),
                    progress = events::add,
                    checkpoints = legacyStore(fixture, baseline),
                ),
            )
            assertEquals(baseline, resumed)
            assertEquals(4, resumed.budgets.size)
            assertEquals(1, events.count { it.boundary == ContextProgressBoundary.CHECKPOINT_LOADED })
            assertTrue(events.none { it.boundary == ContextProgressBoundary.ANALYSIS_STARTED })
            events.clear()
            val current = measure(fixture, fixture.pipeline, events)
            events.clear()
            assertEquals(current, measure(fixture, fixture.pipeline, events))
            assertEquals(1, events.count { it.boundary == ContextProgressBoundary.CHECKPOINT_LOADED })
        }
    }

    private fun legacyStore(
        fixture: ContextAnalysisIdentityTest.Fixture,
        baseline: ContextSetResult,
    ): FileContextTaskCheckpoints {
        val options = ContextBenchmarkOptions(estimator = CountingEstimator())
        val graph = fixture.scope.graph()
        val corpus = LocalizationCorpus.load(fixture.root, graph.artifacts(), WorkspaceNames.of(graph))
        val configuration = "${options.budgets}:${options.methods}:${options.estimator.id}"
        val fingerprint = contextFingerprint(
            ContextSnapshot.fingerprint(graph, corpus) + configuration + Json.encodeToString(fixture.task),
        )
        val request = ContextCheckpointRequest(BenchmarkSet.ORIGINAL, fixture.task.id, fingerprint)
        FileContextTaskCheckpoints(directory.resolve("legacy"), "same-freeze").save(
            request,
            baseline.budgets.associate { it.budget to it.tasks.single() },
        )
        return FileContextTaskCheckpoints(
            directory.resolve("legacy"),
            "same-freeze",
            analysisKeys = options.selection.analysisKeys,
        )
    }

    @Test
    fun `source or estimator changes still invalidate checkpoints`() {
        ContextAnalysisIdentityTest.Fixture(directory).use { fixture ->
            val events = mutableListOf<ContextProgressEvent>()
            val original = measure(fixture, fixture.pipeline)
            Files.writeString(
                fixture.root.resolve(ContextAnalysisIdentityTest.Fixture.SOURCE),
                "# Anchor\nChanged anchor.\n",
            )
            val changed = measure(fixture, fixture.pipeline, events)
            assertNotEquals(original, changed)
            assertTrue(events.none { it.boundary == ContextProgressBoundary.CHECKPOINT_LOADED })
            events.clear()
            val estimator = object : TokenEstimator by CountingEstimator() {
                override val id = TokenizerId("other")
            }
            ContextBenchmark.evaluate(
                fixture.scope,
                fixture.root,
                listOf(fixture.task),
                ContextBenchmarkOptions(
                    selection = ContextBenchmarkSelection(ContextBenchmarkOptions.DEFAULT_METHODS, fixture.pipeline),
                    estimator = estimator,
                    progress = events::add,
                    checkpoints = store(),
                ),
            )
            assertTrue(events.none { it.boundary == ContextProgressBoundary.CHECKPOINT_LOADED })
        }
    }

    private fun measure(
        fixture: ContextAnalysisIdentityTest.Fixture,
        pipeline: ContextPipelineIdentity? = null,
        events: MutableList<ContextProgressEvent> = mutableListOf(),
    ): ContextSetResult = ContextBenchmark.evaluate(
        fixture.scope,
        fixture.root,
        listOf(fixture.task),
        ContextBenchmarkOptions(
            selection = ContextBenchmarkSelection(ContextBenchmarkOptions.DEFAULT_METHODS, pipeline),
            estimator = CountingEstimator(),
            progress = events::add,
            checkpoints = store(),
        ),
    )

    private fun store(keys: List<ContextAnalysisKey> = emptyList()): FileContextTaskCheckpoints =
        FileContextTaskCheckpoints(directory.resolve("checkpoints"), "same-freeze", analysisKeys = keys)
}
