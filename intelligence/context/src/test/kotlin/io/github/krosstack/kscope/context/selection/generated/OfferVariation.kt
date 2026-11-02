package io.github.krosstack.kscope.context.selection.generated

import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.model.NodeRef
import kotlin.random.Random

internal class OfferVariation(private val random: Random, private val profile: CaseProfile = CaseProfile.ORDINARY) {
    fun vary(offers: List<ContextOffer>): List<ContextOffer> {
        if (profile == CaseProfile.TWINS) return offers.shuffled(random)
        val extended = offers.map(::withMoreVariants).map(::withoutValue)
        val duplicates = extended.filter { random.chance(DUPLICATE_PERCENT) }.map(::duplicate)
        return (extended + duplicates).shuffled(random)
    }

    private fun withMoreVariants(offer: ContextOffer): ContextOffer {
        var variants = offer.variants
        while (variants.size < MAX_VARIANTS && random.chance(EXTRA_VARIANT_PERCENT)) {
            variants = variants + variants.last().copy(relevance = variants.last().relevance * random.pick(FACTORS))
        }
        return ContextOffer(variants)
    }

    private fun withoutValue(offer: ContextOffer): ContextOffer {
        if (profile != CaseProfile.TIES) return offer
        val zero = offer.variants.first().node is NodeRef.OfArtifact || random.chance(ZERO_PERCENT)
        return if (zero) ContextOffer(offer.variants.map { it.copy(relevance = 0.0) }) else offer
    }

    private fun duplicate(offer: ContextOffer): ContextOffer {
        if (random.chance(EXACT_PERCENT)) return offer
        val last = offer.variants.last()
        return ContextOffer(offer.variants.dropLast(1) + last.copy(relevance = last.relevance * random.pick(FACTORS)))
    }

    private companion object {
        const val DUPLICATE_PERCENT = 20
        const val EXTRA_VARIANT_PERCENT = 25
        const val EXACT_PERCENT = 50
        const val ZERO_PERCENT = 60
        const val MAX_VARIANTS = 3
        val FACTORS = listOf(1.0, 2.0, 0.5, 1.0 / 3)
    }
}
