package io.github.krosstack.kscope.context

import io.github.krosstack.kscope.context.estimate.EstimateCase
import io.github.krosstack.kscope.context.estimate.EstimateRuns
import io.github.krosstack.kscope.context.estimate.RecordingMeasureStore
import io.github.krosstack.kscope.context.estimate.estimateCases
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.spi.context.TokenEstimator
import kotlin.test.Test
import kotlin.test.assertEquals

internal class PackageEstimateTest {
    private val estimators: List<TokenEstimator> =
        TokenEstimators.supported.map { TokenEstimators.create(it) } + CharacterEstimator()

    @Test
    fun `estimate gives the total of the compiled package for every case level and estimator`() {
        for (case in estimateCases()) {
            for (estimator in estimators) {
                val compiler = ContextCompiler(case.fixture, case.fixture, estimator)
                for (level in ContextLevel.entries) assertSameTotal(compiler, estimator, case, level)
            }
        }
    }

    @Test
    fun `estimate gives the total of the compiled package in every state of the token count store`() {
        for (state in StoreState.entries) {
            for (case in estimateCases()) {
                for (estimator in estimators) assertInState(state, case, estimator)
            }
        }
    }

    private fun assertInState(state: StoreState, case: EstimateCase, estimator: TokenEstimator) {
        val compiler = prepare(state, case, estimator)
        for (level in ContextLevel.entries) assertSameTotal(compiler, estimator, case, level, state)
    }

    private fun assertSameTotal(
        compiler: ContextCompiler,
        estimator: TokenEstimator,
        case: EstimateCase,
        level: ContextLevel,
        state: StoreState? = null,
    ) {
        val compiled = EstimateRuns.outcome { compiler.compile(case.input, level).totalTokens }
        val estimated = EstimateRuns.outcome { compiler.estimate(case.input, level) }

        assertEquals(compiled, estimated, "case=${case.name} estimator=${estimator.id.value} level=$level $state")
    }

    private fun prepare(state: StoreState, case: EstimateCase, estimator: TokenEstimator): ContextCompiler =
        when (state) {
            StoreState.NONE -> ContextCompiler(case.fixture, case.fixture, estimator)

            StoreState.EMPTY -> over(case, estimator, RecordingMeasureStore())

            StoreState.FILLED_BY_THE_SAME_COMPILER -> over(case, estimator, RecordingMeasureStore()).also {
                EstimateRuns.compileAndEstimateEachLevel(it, case)
            }

            StoreState.TEXT_OF_THE_GRAPH_CHANGED -> {
                val reworded = case.fixture.withSignaturesReworded()
                ContextCompiler(reworded, reworded, estimator, filled(case, estimator))
            }

            StoreState.FILLED_BY_ANOTHER_COMPILER,
            StoreState.OTHER_KEYS_ONLY,
            StoreState.BLOCKS_FROM_THE_GRAPH_ONLY,
            StoreState.BLOCKS_FROM_FILES_ONLY,
            -> over(case, estimator, state.keep(filled(case, estimator)))
        }

    private fun filled(case: EstimateCase, estimator: TokenEstimator): RecordingMeasureStore =
        RecordingMeasureStore().also {
            EstimateRuns.compileAndEstimateEachLevel(over(case, estimator, it), case)
        }

    private fun over(case: EstimateCase, estimator: TokenEstimator, store: RecordingMeasureStore): ContextCompiler =
        ContextCompiler(case.fixture, case.fixture, estimator, store)

    private enum class StoreState(val keep: (RecordingMeasureStore) -> RecordingMeasureStore = { it }) {
        NONE,
        EMPTY,
        FILLED_BY_THE_SAME_COMPILER,
        FILLED_BY_ANOTHER_COMPILER,
        OTHER_KEYS_ONLY({ it.withOtherKeysOnly() }),
        BLOCKS_FROM_THE_GRAPH_ONLY({ it.ofGraphBlocksOnly() }),
        BLOCKS_FROM_FILES_ONLY({ it.ofFileBlocksOnly() }),
        TEXT_OF_THE_GRAPH_CHANGED,
    }
}
