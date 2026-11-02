package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.CharacterEstimator
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SelectionAccountingTest {
    @Test
    fun `selections match the frozen algorithm with unresolved relationships under every estimator`() {
        val fixture = AccountingFixture(fileCount = 7, declarationsPerFile = 5)
        val estimators = listOf(
            TokenEstimators.create(),
            TokenEstimators.create(TokenizerId("cl100k_base")),
            TokenEstimators.create(TokenizerId("chars_4")),
            CharacterEstimator(),
        )
        for (estimator in estimators) {
            for (level in listOf(ContextLevel.MINIMAL, ContextLevel.RECOMMENDED, ContextLevel.FULL)) {
                val offers = fixture.offers(level, estimator)
                for (budget in listOf(1, 120, 400, 1500, 6000)) {
                    val frozen = FrozenSelectorV1(fixture.merge(estimator), estimator, fixture.graph)
                        .select(fixture.input, offers, TokenCount(budget))
                    val actual = ContextSelector(fixture.merge(estimator), estimator, fixture.graph)
                        .select(fixture.input, offers, TokenCount(budget))

                    assertEquals(frozen, actual, "estimator=${estimator.id.value} level=$level budget=$budget")
                }
            }
        }
    }

    @Test
    fun `offers whose variants differ in node fall back to the rescanning choice with the same result`() {
        val fixture = AccountingFixture(fileCount = 4, declarationsPerFile = 3)
        val estimator = TokenEstimators.create()
        val offers = fixture.offers(ContextLevel.RECOMMENDED, estimator)
        val mixed = offers + ContextOffer(listOf(offers[0].variants.first(), offers[1].variants.last()))

        assertFalse(IncrementalChoice.applies(mixed, fixture.graph))
        for (budget in listOf(80, 600, 3000)) {
            val frozen = FrozenSelectorV1(fixture.merge(estimator), estimator, fixture.graph)
                .select(fixture.input, mixed, TokenCount(budget))
            val actual = ContextSelector(fixture.merge(estimator), estimator, fixture.graph)
                .select(fixture.input, mixed, TokenCount(budget))

            assertEquals(frozen, actual, "budget=$budget")
        }
    }

    @Test
    fun `tokenizer work of a selection grows linearly with the number of candidate files`() {
        val small = tokenizerWork(fileCount = SMALL_FILES)
        val large = tokenizerWork(fileCount = SMALL_FILES * SCALE)

        assertEquals(0, large.work.packages, "no package is tokenized whole during selection: $large")
        assertTrue(large.work.total <= SCALE * LINEAR_SLACK * small.work.total, "small=$small large=$large")
        assertTrue(large.work.total <= large.offers * CALLS_PER_OFFER, "large=$large")
    }

    private fun tokenizerWork(fileCount: Int): TokenizerWork {
        val fixture = AccountingFixture(fileCount, declarationsPerFile = DECLARATIONS)
        val counting = AdditiveCountingEstimator(TokenEstimators.create())
        val offers = fixture.offers(ContextLevel.RECOMMENDED, counting)
        val before = counting.snapshot()

        val selection = ContextSelector(fixture.merge(counting), counting, fixture.graph)
            .select(fixture.input, offers, TokenCount(BUDGET))

        assertTrue(selection.fragments.isNotEmpty())
        return TokenizerWork(offers.size, counting.snapshot() - before)
    }

    private data class TokenizerWork(val offers: Int, val work: AdditiveCountingEstimator.Work)

    private companion object {
        const val BUDGET = 16_000
        const val SMALL_FILES = 6
        const val SCALE = 4
        const val DECLARATIONS = 6
        const val LINEAR_SLACK = 2
        const val CALLS_PER_OFFER = 4
    }
}
