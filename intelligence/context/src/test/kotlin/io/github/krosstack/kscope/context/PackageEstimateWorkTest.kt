package io.github.krosstack.kscope.context

import io.github.krosstack.kscope.context.estimate.EstimateRuns
import io.github.krosstack.kscope.context.estimate.RecordingBlockEstimator
import io.github.krosstack.kscope.context.estimate.estimateCase
import io.github.krosstack.kscope.context.estimate.estimateCases
import io.github.krosstack.kscope.context.selection.CountingTokenEstimator
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenizerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class PackageEstimateWorkTest {
    private val characters = TokenEstimators.create(TokenizerId("chars_4"))

    @Test
    fun `one estimate reads each file at most once`() {
        for (case in estimateCases()) {
            ContextCompiler(case.fixture, case.fixture, characters).estimate(case.input, ContextLevel.FULL)

            assertTrue(case.fixture.reads.values.all { it <= 1 }, case.name)
        }
    }

    @Test
    fun `four estimates on one compiler read each file at most once`() {
        for (case in estimateCases()) {
            EstimateRuns.estimateEachLevel(ContextCompiler(case.fixture, case.fixture, characters), case)

            assertTrue(case.fixture.reads.values.all { it <= 1 }, case.name)
        }
        val identity = estimateCase(IDENTITY)
        EstimateRuns.estimateEachLevel(ContextCompiler(identity.fixture, identity.fixture, characters), identity)
        assertTrue(identity.fixture.reads.isNotEmpty())
    }

    @Test
    fun `an estimator that is not block additive is asked for the whole text once`() {
        val case = estimateCase(IDENTITY)
        val counting = CountingTokenEstimator(CharacterEstimator())

        val estimate = ContextCompiler(case.fixture, case.fixture, counting).estimate(case.input, ContextLevel.FULL)

        val compiled = ContextCompiler(case.fixture, case.fixture, CharacterEstimator())
            .compile(case.input, ContextLevel.FULL)
        assertEquals(1, counting.calls)
        assertEquals(compiled.totalTokens, estimate)
    }

    @Test
    fun `an additive estimate of a package with valid blocks counts no whole text`() {
        for (case in estimateCases().filter { "surrogate" !in it.name }) {
            for (estimator in TokenEstimators.supported.map { TokenEstimators.create(it) }) {
                val recording = RecordingBlockEstimator(estimator)

                EstimateRuns.estimateEachLevel(ContextCompiler(case.fixture, case.fixture, recording), case)

                assertEquals(emptyList(), recording.counted, "case=${case.name} estimator=${estimator.id.value}")
            }
        }
    }

    @Test
    fun `a block that is not standalone makes an additive estimate count the whole text`() {
        for (name in listOf(LONE_SURROGATE_PATH, LONE_SURROGATE_TASK)) {
            val case = estimateCase(name)
            val compiled = ContextCompiler(case.fixture, case.fixture, characters)
                .compile(case.input, ContextLevel.FULL)
            val recording = RecordingBlockEstimator(characters)

            val estimate = ContextCompiler(case.fixture, case.fixture, recording)
                .estimate(case.input, ContextLevel.FULL)

            assertEquals(listOf(compiled.markdown), recording.counted, name)
            assertEquals(compiled.totalTokens, estimate, name)
        }
    }

    @Test
    fun `compile still counts every fragment after an estimate and gives the same package`() {
        val case = estimateCase(IDENTITY)
        val estimator = TokenEstimators.create()
        val compiler = ContextCompiler(case.fixture, case.fixture, estimator)
        val before = compiler.compile(case.input, ContextLevel.FULL)

        compiler.estimate(case.input, ContextLevel.FULL)
        val after = compiler.compile(case.input, ContextLevel.FULL)

        assertEquals(before, after)
        assertTrue(after.fragments.isNotEmpty())
        after.fragments.forEach { assertEquals(estimator.count(it.text), it.tokens) }
    }

    private companion object {
        const val IDENTITY = "the identity workspace"
        const val LONE_SURROGATE_PATH = "lone high surrogate in the path as a file"
        const val LONE_SURROGATE_TASK = "lone surrogate in the task as a file"
    }
}
