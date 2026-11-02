package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.context.fragment.FragmentMerge
import io.github.krosstack.kscope.context.fragment.contextNodeKey
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextOmission
import io.github.krosstack.kscope.model.context.ContextOmissionReason
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal data class ContextSelection(val fragments: List<ContextFragment>, val omitted: List<ContextOmission>)

internal class FrozenSelectorV1(
    private val merge: FragmentMerge,
    private val estimator: TokenEstimator,
    private val graph: ContextGraph,
) {
    fun select(input: ContextInput, offers: List<ContextOffer>, budget: TokenCount?): ContextSelection {
        val unique = offers.groupBy { it.variants.first().node }.map { (_, matches) ->
            matches.maxWith(compareBy<ContextOffer> { it.variants.last().relevance }.thenBy { it.variants.size })
        }.sortedBy { contextNodeKey(it.variants.first().node) }
        val selected = if (budget == null) {
            unique.associate { it.variants.last().node to it.variants.last() }
        } else {
            choose(input, unique, budget)
        }
        val fragments = merge.normalize(selected.values.toList())
        val omitted = unique.filter { it.variants.first().node !in selected }
            .map { ContextOmission(it.variants.first().node, ContextOmissionReason.BUDGET) }
        return ContextSelection(fragments, omitted)
    }

    private fun choose(
        input: ContextInput,
        offers: List<ContextOffer>,
        budget: TokenCount,
    ): Map<NodeRef, ContextFragment> {
        val current = mutableMapOf<NodeRef, ContextFragment>()
        val positions = mutableMapOf<NodeRef, Int>()
        while (true) {
            val previous = merge.normalize(current.values.toList())
            val previousTokens = count(input, previous)
            val proposals = offers.mapNotNull { offer ->
                proposal(input, offer, positions, current, previousTokens)
            }.filter { it.tokens <= budget.value && it.gain >= 0 }
            val best = proposals.maxWithOrNull(
                compareBy<FrozenSelectionProposal> { it.density }
                    .thenByDescending { contextNodeKey(it.fragment.node) },
            ) ?: break
            current[best.fragment.node] = best.fragment
            positions[best.fragment.node] = best.position
        }
        return current
    }

    private fun proposal(
        input: ContextInput,
        offer: ContextOffer,
        positions: Map<NodeRef, Int>,
        current: Map<NodeRef, ContextFragment>,
        previousTokens: Int,
    ): FrozenSelectionProposal? {
        val node = offer.variants.first().node
        val position = (positions[node] ?: -1) + 1
        val fragment = offer.variants.getOrNull(position) ?: return null
        val before = merge.normalize(current.values.toList())
        val after = merge.normalize((current + (node to fragment)).values.toList())
        val tokens = count(input, after)
        val gain = after.sumOf(FrozenFragmentUtility::value) - before.sumOf(FrozenFragmentUtility::value)
        return FrozenSelectionProposal(
            fragment,
            position,
            tokens,
            gain,
            gain / (tokens - previousTokens).coerceAtLeast(1),
        )
    }

    private fun count(input: ContextInput, fragments: List<ContextFragment>): Int {
        val files = fragments.map { it.artifact }.toSet()
        val unresolved = input.unresolved.filter { graph.artifact(it.from) in files }
        return estimator.count(ContextMarkdown.renderContents(input.task, fragments, unresolved)).value
    }
}

internal object FrozenFragmentUtility {
    fun value(fragment: ContextFragment): Double = fragment.relevance * when (fragment.rendering) {
        ContextRendering.SIGNATURE -> SIGNATURE_UTILITY
        ContextRendering.DECLARATION, ContextRendering.ENTRY, ContextRendering.FILE -> 1.0
        ContextRendering.OUTLINE -> OUTLINE_UTILITY
        ContextRendering.GENERATED -> GENERATED_UTILITY
    }

    private const val SIGNATURE_UTILITY = 0.35
    private const val OUTLINE_UTILITY = 0.5
    private const val GENERATED_UTILITY = 0.2
}

private data class FrozenSelectionProposal(
    val fragment: ContextFragment,
    val position: Int,
    val tokens: Int,
    val gain: Double,
    val density: Double,
)
