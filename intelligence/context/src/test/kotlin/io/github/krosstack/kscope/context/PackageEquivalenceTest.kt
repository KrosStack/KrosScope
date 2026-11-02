package io.github.krosstack.kscope.context

import io.github.krosstack.kscope.context.selection.generated.GeneratedSelection
import io.github.krosstack.kscope.context.selection.generated.SelectionCaseGenerator
import io.github.krosstack.kscope.context.selection.generated.budgetsUpTo
import io.github.krosstack.kscope.context.selection.legacy.LegacyContextCompiler
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.spi.context.TokenEstimator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class PackageEquivalenceTest {
    @Test
    fun `packages equal those built with the legacy selection under a block additive estimator`() {
        assertEquivalent(TokenEstimators.create(), countsLoneSurrogates = false)
    }

    @Test
    fun `packages equal those built with the legacy selection under an estimator that is not block additive`() {
        assertEquivalent(CharacterEstimator(), countsLoneSurrogates = true)
    }

    private fun assertEquivalent(estimator: TokenEstimator, countsLoneSurrogates: Boolean) {
        val cases = SEEDS.map { SelectionCaseGenerator(it).generate() }
            .filter { countsLoneSurrogates || !it.hasLoneSurrogates }

        assertTrue(cases.size >= MINIMUM_CASES, "cases=${cases.size}")
        assertEquals(emptyList(), cases.flatMap { differences(it, estimator) })
    }

    private fun differences(case: GeneratedSelection, estimator: TokenEstimator): List<String> =
        ContextLevel.entries.flatMap { level ->
            val current = ContextCompiler(case.fixture, case.fixture, estimator, TokenCount(0))
            val legacy = LegacyContextCompiler(case.fixture, case.fixture, estimator)
            val unbudgeted = current.compile(case.input, level)
            val budgets = listOf(null) + budgetsUpTo(unbudgeted.totalTokens.value, case.seed)
            budgets.mapNotNull { budget ->
                val count = budget?.let(::TokenCount)
                val expected = legacy.compile(case.input, level, count)
                val actual = current.compile(case.input, level, count)
                "seed=${case.seed} estimator=${estimator.id.value} level=$level budget=$budget"
                    .takeIf { expected != actual }
            }
        }

    private companion object {
        val SEEDS = 1L..360L step 7
        const val MINIMUM_CASES = 40
    }
}
