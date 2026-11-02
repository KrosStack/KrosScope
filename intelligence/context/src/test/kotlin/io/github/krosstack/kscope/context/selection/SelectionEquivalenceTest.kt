package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.CharacterEstimator
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.context.selection.generated.GeneratedSelection
import io.github.krosstack.kscope.context.selection.generated.SelectionCaseGenerator
import io.github.krosstack.kscope.context.selection.generated.budgetsUpTo
import io.github.krosstack.kscope.context.selection.legacy.LegacyContextSelector
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.spi.context.TokenEstimator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SelectionEquivalenceTest {
    @Test
    fun `the selection equals the legacy selection under o200k_base`() {
        assertEquivalent(TokenEstimators.create(TokenizerId("o200k_base")), countsLoneSurrogates = false)
    }

    @Test
    fun `the selection equals the legacy selection under cl100k_base`() {
        assertEquivalent(TokenEstimators.create(TokenizerId("cl100k_base")), countsLoneSurrogates = false)
    }

    @Test
    fun `the selection equals the legacy selection under chars_4`() {
        assertEquivalent(TokenEstimators.create(TokenizerId("chars_4")), countsLoneSurrogates = true)
    }

    @Test
    fun `the selection equals the legacy selection under an estimator that is not block additive`() {
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
            val offers = case.offers(level, estimator)
            val budgets = listOf(null) + budgetsUpTo(case.unbudgetedTokens(estimator, offers), case.seed)
            budgets.mapNotNull { budget ->
                val expected = legacy(case, estimator, offers, budget)
                val actual = ContextSelector(case.merge(estimator), estimator, case.graph)
                    .select(case.input, offers, budget?.let(::TokenCount))
                "seed=${case.seed} estimator=${estimator.id.value} level=$level budget=$budget"
                    .takeIf { expected != actual }
            }
        }

    private fun legacy(case: GeneratedSelection, estimator: TokenEstimator, offers: List<ContextOffer>, budget: Int?) =
        LegacyContextSelector(case.merge(estimator), estimator, case.graph)
            .select(case.input, offers, budget?.let(::TokenCount))

    private companion object {
        val SEEDS = 1L..720L
        const val MINIMUM_CASES = 600
    }
}
