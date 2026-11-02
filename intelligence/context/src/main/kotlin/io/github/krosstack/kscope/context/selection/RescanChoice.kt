package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.context.fragment.FragmentMerge
import io.github.krosstack.kscope.context.fragment.contextNodeKey
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.TokenCount

internal class RescanChoice(
    private val offers: List<ContextOffer>,
    private val budget: TokenCount,
    private val merge: FragmentMerge,
    private val counting: ExactCounting,
    private val unresolved: UnresolvedCounts,
) {
    fun choose(): Map<NodeRef, ContextFragment> {
        val current = LinkedHashMap<NodeRef, ContextFragment>()
        val positions = HashMap<NodeRef, Int>()
        while (true) {
            val previous = merge.normalize(current.values.toList())
            val baseline = SelectionBaseline(tokensOf(previous), valueOf(previous))
            val best = offers.mapNotNull { proposal(it, current, positions, baseline) }
                .filter { it.tokens <= budget.value && it.gain >= 0 }
                .maxWithOrNull(SelectionProposal.PREFERENCE) ?: break
            current[best.fragment.node] = best.fragment
            positions[best.fragment.node] = best.position
        }
        return current
    }

    private fun proposal(
        offer: ContextOffer,
        current: Map<NodeRef, ContextFragment>,
        positions: Map<NodeRef, Int>,
        baseline: SelectionBaseline,
    ): SelectionProposal? {
        val node = offer.variants.first().node
        val position = (positions[node] ?: -1) + 1
        val fragment = offer.variants.getOrNull(position) ?: return null
        val after = merge.normalize((current + (node to fragment)).values.toList())
        val tokens = tokensOf(after)
        val gain = valueOf(after) - baseline.value
        return SelectionProposal(
            fragment,
            position,
            tokens,
            gain,
            gain / (tokens - baseline.tokens).coerceAtLeast(1),
            contextNodeKey(fragment.node),
        )
    }

    private fun tokensOf(fragments: List<ContextFragment>): Int =
        counting.tokens(fragments, unresolved.ofAll(fragments.map { it.artifact }.toSet()))

    private fun valueOf(fragments: List<ContextFragment>): Double = fragments.sumOf(FragmentUtility::value)
}
