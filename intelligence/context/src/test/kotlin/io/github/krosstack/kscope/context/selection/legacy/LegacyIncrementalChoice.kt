package io.github.krosstack.kscope.context.selection.legacy

import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.TokenCount

internal class LegacyIncrementalChoice(
    private val offers: List<ContextOffer>,
    private val budget: TokenCount,
    private val ledger: LegacySelectionLedger,
) {
    fun choose(): Map<NodeRef, ContextFragment> {
        while (true) {
            val baseline = ledger.baseline()
            val best = offers.mapNotNull { ledger.propose(it, baseline) }
                .filter { it.tokens <= budget.value && it.gain >= 0 }
                .maxWithOrNull(LegacySelectionProposal.PREFERENCE) ?: break
            ledger.accept(best)
        }
        return ledger.selected
    }

    companion object {
        fun applies(offers: List<ContextOffer>, graph: ContextGraph): Boolean =
            offers.all(::variantsShareNodeAndFile) && fileOrderIsStrict(offers, graph)

        fun fileOrder(graph: ContextGraph): Comparator<ArtifactRef> =
            compareBy<ArtifactRef> { graph.module(it) }.thenBy { it.path.value }

        private fun variantsShareNodeAndFile(offer: ContextOffer): Boolean {
            val first = offer.variants.first()
            return offer.variants.all { it.node == first.node && it.artifact == first.artifact }
        }

        private fun fileOrderIsStrict(offers: List<ContextOffer>, graph: ContextGraph): Boolean {
            val files = offers.map { it.variants.first().artifact }.toSet()
            return files.map { graph.module(it) to it.path.value }.toSet().size == files.size
        }
    }
}
