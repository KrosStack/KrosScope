package io.github.krosstack.kscope.context

import io.github.krosstack.kscope.context.estimate.EstimateRuns
import io.github.krosstack.kscope.context.estimate.RecordingBlockEstimator
import io.github.krosstack.kscope.context.estimate.RecordingMeasureStore
import io.github.krosstack.kscope.context.estimate.estimateCase
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.TokenizerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class PackageEstimateStoreTest {
    private val additive = TokenEstimators.supported.map { TokenEstimators.create(it) }
    private val characters = TokenEstimators.create(TokenizerId("chars_4"))

    @Test
    fun `with a filled store an additive estimate counts no fragment block of any rendering`() {
        for (estimator in additive) {
            val case = estimateCase(IDENTITY)
            val store = RecordingMeasureStore()
            val cold = RecordingBlockEstimator(estimator)
            val warm = RecordingBlockEstimator(estimator)

            EstimateRuns.estimateEachLevel(ContextCompiler(case.fixture, case.fixture, cold, store), case)
            EstimateRuns.estimateEachLevel(ContextCompiler(case.fixture, case.fixture, warm, store), case)

            val label = "estimator=${estimator.id.value}"
            val fragments = EstimateRuns.fragmentBlocksOf(case, estimator)
            assertTrue(fragments.any { it.persisted }, label)
            assertTrue(fragments.any { !it.persisted }, label)
            assertTrue(cold.measured.toSet().containsAll(fragments.map { it.text }), label)
            assertEquals(emptyList(), warm.counted, label)
            assertTrue(EstimateRuns.fixedBlocksOf(case, estimator).containsAll(warm.measured), label)
            assertTrue(store.hits > 0, label)
        }
    }

    @Test
    fun `the measure of a character estimator is kept as code points and rounded once over the sum`() {
        val case = estimateCase(IDENTITY)
        val store = RecordingMeasureStore()
        val compiled = ContextCompiler(case.fixture, case.fixture, characters).compile(case.input, ContextLevel.FULL)
        val allBlocks = ContextMarkdown.blocks(compiled.task, compiled.fragments, compiled.unresolved)
        assertNotEquals(allBlocks.sumOf { (codePoints(it) + CHARS - 1) / CHARS }, compiled.totalTokens.value)

        ContextCompiler(case.fixture, case.fixture, characters, store).estimate(case.input, ContextLevel.FULL)
        val estimated = ContextCompiler(case.fixture, case.fixture, characters, store)
            .estimate(case.input, ContextLevel.FULL)

        val kept = compiled.fragments.map { codePoints(ContextMarkdown.fragment(it)) }
        assertEquals(compiled.totalTokens, estimated)
        assertEquals(kept.sorted(), store.measures.values.map { it.toInt() }.sorted())
        assertTrue(store.offers.any { it.rendering !in EstimateRuns.persisted })
    }

    @Test
    fun `a measure of a block that comes from the graph is used when its key is found`() {
        val case = estimateCase(IDENTITY)
        val estimator = TokenEstimators.create()
        val honest = RecordingMeasureStore()
        EstimateRuns.compileAndEstimateEachLevel(ContextCompiler(case.fixture, case.fixture, estimator, honest), case)
        val poisoned = honest.withGraphBlocksRaised(POISON)

        for (level in ContextLevel.entries) {
            val compiled = ContextCompiler(case.fixture, case.fixture, estimator).compile(case.input, level)
            val estimated = ContextCompiler(case.fixture, case.fixture, estimator, poisoned).estimate(case.input, level)

            val fromGraph = compiled.fragments.count { it.rendering !in EstimateRuns.persisted }
            assertEquals(compiled.totalTokens.value + POISON * fromGraph, estimated.value.toLong(), "level=$level")
        }
        assertTrue(poisoned.measures.keys.any { it.textDigest != null })
    }

    @Test
    fun `a measure kept for a text is not answered for the changed text of a file that did not change`() {
        val case = estimateCase(IDENTITY)
        val store = RecordingMeasureStore()
        val reworded = case.fixture.withSignaturesReworded()
        EstimateRuns.compileAndEstimateEachLevel(ContextCompiler(case.fixture, case.fixture, characters, store), case)

        val before = ContextLevel.entries.map { level ->
            ContextCompiler(case.fixture, case.fixture, characters).compile(case.input, level).totalTokens
        }
        val after = ContextLevel.entries.map { level ->
            ContextCompiler(reworded, reworded, characters).compile(case.input, level).totalTokens
        }
        val estimated = ContextLevel.entries.map { level ->
            ContextCompiler(reworded, reworded, characters, store).estimate(case.input, level)
        }

        assertNotEquals(before, after)
        assertEquals(after, estimated)
    }

    @Test
    fun `a block with a path that is not valid Unicode is neither recalled nor offered`() {
        val case = estimateCase(LONE_SURROGATE_PATH)
        val store = RecordingMeasureStore()

        ContextCompiler(case.fixture, case.fixture, characters, store).estimate(case.input, ContextLevel.FULL)

        assertEquals(emptyList(), store.recalls)
        assertEquals(emptyList(), store.offers)
    }

    @Test
    fun `a signature that is not valid Unicode is neither recalled nor offered`() {
        val case = estimateCase(LONE_SURROGATE_SIGNATURE)
        val store = RecordingMeasureStore()

        repeat(2) {
            ContextCompiler(case.fixture, case.fixture, characters, store)
                .estimate(case.input, ContextLevel.RECOMMENDED)
        }

        val signatures = store.offers.filter { it.rendering == ContextRendering.SIGNATURE }
        assertEquals(1, signatures.size)
        assertEquals(listOf(signatures.single(), signatures.single()), store.recalls)
    }

    private fun codePoints(text: String): Int = text.codePointCount(0, text.length)

    private companion object {
        const val IDENTITY = "the identity workspace"
        const val LONE_SURROGATE_PATH = "lone high surrogate in the path as a file"
        const val LONE_SURROGATE_SIGNATURE = "lone surrogate in a signature"
        const val CHARS = 4
        const val POISON = 1_000L
    }
}
