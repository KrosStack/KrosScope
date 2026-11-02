package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.TokenCount

internal class IncrementalChoice(
    private val offers: List<KeyedOffer>,
    private val budget: TokenCount,
    private val ledger: SelectionLedger,
) {
    fun choose(): Map<NodeRef, ContextFragment> {
        val remaining = ledger.cursors(offers).toMutableList()
        while (true) {
            val baseline = ledger.baseline()
            val best = bestOf(remaining, baseline) ?: break
            ledger.accept(best.cursor, best.proposal)
            if (!best.cursor.hasNext) remaining.remove(best.cursor)
        }
        return ledger.selected
    }

    private fun bestOf(remaining: List<OfferCursor>, baseline: SelectionBaseline): PricedOffer? {
        var best: PricedOffer? = null
        for (cursor in remaining) {
            val proposal = ledger.propose(cursor, baseline)
            if (fits(proposal) && isPreferredTo(best, proposal)) best = PricedOffer(cursor, proposal)
        }
        return best
    }

    private fun fits(proposal: SelectionProposal): Boolean = proposal.tokens <= budget.value && proposal.gain >= 0

    // An equal proposal does not replace the best: the first of equal maxima wins, as in every earlier version.
    private fun isPreferredTo(best: PricedOffer?, proposal: SelectionProposal): Boolean =
        best == null || SelectionProposal.PREFERENCE.compare(best.proposal, proposal) < 0

    companion object {
        fun applies(offers: List<ContextOffer>, graph: ContextGraph): Boolean =
            offers.all(::variantsShareNodeAndFile) && fileOrderIsStrict(offers, graph)

        fun fileOrder(graph: ContextGraph): Comparator<ArtifactRef> =
            compareBy<ArtifactRef> { graph.module(it) }.thenBy { graph.locations.pathOf(it) }

        private fun variantsShareNodeAndFile(offer: ContextOffer): Boolean {
            val first = offer.variants.first()
            return offer.variants.all { it.node == first.node && it.artifact == first.artifact }
        }

        private fun fileOrderIsStrict(offers: List<ContextOffer>, graph: ContextGraph): Boolean {
            val files = offers.map { it.variants.first().artifact }.toSet()
            return files.map { graph.module(it) to graph.locations.pathOf(it) }.toSet().size == files.size
        }
    }
}

private class PricedOffer(val cursor: OfferCursor, val proposal: SelectionProposal)
