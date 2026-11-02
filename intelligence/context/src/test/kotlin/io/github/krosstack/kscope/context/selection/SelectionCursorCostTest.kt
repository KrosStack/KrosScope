package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.CharacterEstimator
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.context.selection.legacy.LegacyContextSelector
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SelectionCursorCostTest {
    @Test
    fun `the variants of an offer are read when the offer is accepted and not at every proposal`() {
        val fixture = AccountingFixture(LARGE_FILES, DECLARATIONS)
        val estimator = TokenEstimators.create(TokenizerId("chars_4"))
        val offers = fixture.offers(ContextLevel.RECOMMENDED, estimator)
        val legacyReads = Reads()
        val currentReads = Reads()

        val expected = LegacyContextSelector(fixture.merge(estimator), estimator, fixture.graph)
            .select(fixture.input, offers.map { counted(it, legacyReads) }, TokenCount(BUDGET))
        val actual = ContextSelector(fixture.merge(estimator), estimator, fixture.graph)
            .select(fixture.input, offers.map { counted(it, currentReads) }, TokenCount(BUDGET))

        println("cursor-cost offers=${offers.size} legacyReads=${legacyReads.count} currentReads=${currentReads.count}")
        assertEquals(expected, actual)
        assertTrue(expected.fragments.isNotEmpty())
        assertTrue(currentReads.count <= offers.size * READS_PER_OFFER, "current=${currentReads.count}")
        assertTrue(
            legacyReads.count >= RATIO * currentReads.count,
            "legacy=${legacyReads.count} current=${currentReads.count}",
        )
    }

    @Test
    fun `the additive path never counts a whole package and measures the blocks it measured before`() {
        val fixture = AccountingFixture(SMALL_FILES, DECLARATIONS)
        val legacyCounting = AdditiveCountingEstimator(TokenEstimators.create(TokenizerId("chars_4")))
        val currentCounting = AdditiveCountingEstimator(TokenEstimators.create(TokenizerId("chars_4")))
        val legacyOffers = fixture.offers(ContextLevel.RECOMMENDED, legacyCounting)
        val currentOffers = fixture.offers(ContextLevel.RECOMMENDED, currentCounting)
        val legacyBefore = legacyCounting.snapshot()
        val currentBefore = currentCounting.snapshot()

        val expected = LegacyContextSelector(fixture.merge(legacyCounting), legacyCounting, fixture.graph)
            .select(fixture.input, legacyOffers, TokenCount(SMALL_BUDGET))
        val actual = ContextSelector(fixture.merge(currentCounting), currentCounting, fixture.graph)
            .select(fixture.input, currentOffers, TokenCount(SMALL_BUDGET))
        val legacyWork = legacyCounting.snapshot() - legacyBefore
        val currentWork = currentCounting.snapshot() - currentBefore

        println("cursor-cost additive legacy=$legacyWork current=$currentWork")
        assertEquals(expected, actual)
        assertTrue(expected.fragments.isNotEmpty())
        assertEquals(0, currentWork.packages)
        assertEquals(legacyWork, currentWork)
    }

    @Test
    fun `the exact path counts the same texts as before`() {
        val fixture = AccountingFixture(SMALL_FILES, DECLARATIONS)
        val legacyCounting = CountingTokenEstimator(CharacterEstimator())
        val currentCounting = CountingTokenEstimator(CharacterEstimator())
        val offers = fixture.offers(ContextLevel.RECOMMENDED, CharacterEstimator())

        val expected = LegacyContextSelector(fixture.merge(legacyCounting), legacyCounting, fixture.graph)
            .select(fixture.input, offers, TokenCount(SMALL_BUDGET))
        val actual = ContextSelector(fixture.merge(currentCounting), currentCounting, fixture.graph)
            .select(fixture.input, offers, TokenCount(SMALL_BUDGET))

        println("cursor-cost exact legacyCalls=${legacyCounting.calls} currentCalls=${currentCounting.calls}")
        assertEquals(expected, actual)
        assertTrue(expected.fragments.isNotEmpty())
        assertTrue(legacyCounting.calls > 0)
        assertEquals(legacyCounting.calls, currentCounting.calls)
    }

    private fun counted(offer: ContextOffer, reads: Reads) = ContextOffer(CountedVariants(offer.variants, reads))

    private class Reads {
        var count = 0
    }

    private class CountedVariants(private val variants: List<ContextFragment>, private val reads: Reads) :
        List<ContextFragment> by variants {
        override val size: Int get() = variants.size.also { reads.count++ }

        override fun isEmpty(): Boolean = variants.isEmpty().also { reads.count++ }

        override fun get(index: Int): ContextFragment = variants[index].also { reads.count++ }

        override fun iterator(): Iterator<ContextFragment> = variants.iterator().also { reads.count++ }
    }

    private companion object {
        const val LARGE_FILES = 200
        const val SMALL_FILES = 10
        const val DECLARATIONS = 5
        const val BUDGET = 16_000
        const val SMALL_BUDGET = 1_500
        const val READS_PER_OFFER = 40
        const val RATIO = 20
    }
}
