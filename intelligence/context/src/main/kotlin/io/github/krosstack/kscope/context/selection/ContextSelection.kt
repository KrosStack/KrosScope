package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.context.fragment.FragmentMerge
import io.github.krosstack.kscope.context.fragment.contextNodeKey
import io.github.krosstack.kscope.context.token.BlockAdditive
import io.github.krosstack.kscope.context.token.ExactMarkdownTokens
import io.github.krosstack.kscope.context.token.RenderedMarkdownBlocks
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextOmission
import io.github.krosstack.kscope.model.context.ContextOmissionReason
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal data class ContextSelection(val fragments: List<ContextFragment>, val omitted: List<ContextOmission>)

internal class ContextSelector(
    private val merge: FragmentMerge,
    private val estimator: TokenEstimator,
    private val graph: ContextGraph,
    private val fileOrder: Comparator<ArtifactRef> = IncrementalChoice.fileOrder(graph),
) {
    private val tokens = ExactMarkdownTokens(estimator)
    private val blocks = RenderedMarkdownBlocks(graph.locations)

    fun select(input: ContextInput, offers: List<ContextOffer>, budget: TokenCount?): ContextSelection {
        val unique = offers.groupBy { it.variants.first().node }.map { (_, matches) ->
            matches.maxWith(compareBy<ContextOffer> { it.variants.last().relevance }.thenBy { it.variants.size })
        }.map { KeyedOffer(it, contextNodeKey(it.variants.first().node)) }.sortedBy { it.nodeKey }
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
        offers: List<KeyedOffer>,
        budget: TokenCount,
    ): Map<NodeRef, ContextFragment> {
        val unresolved = UnresolvedCounts(input.unresolved, graph)
        val counting = ExactCounting(input.task, blocks, tokens)
        val plain = offers.map { it.offer }
        val ranks = fileRanks(plain) ?: return RescanChoice(plain, budget, merge, counting, unresolved).choose()
        val additive = estimator as? BlockAdditive
        val tally = additive?.let { TokenTally(it, input.task) }?.takeIf { it.taskBlockIsValid }
        val costs = additive?.takeIf { tally != null }?.let { BlockCosts(it, blocks) }
        val ledger = SelectionLedger(
            FileRendering(merge, costs),
            TokenAccounts(tally, counting),
            unresolved,
            ranks,
        )
        return IncrementalChoice(offers, budget, ledger).choose()
    }

    private fun fileRanks(offers: List<ContextOffer>): FileRanks? = if (IncrementalChoice.applies(offers, graph)) {
        FileRanks.of(offers.map { it.variants.first().artifact }, fileOrder)
    } else {
        null
    }
}

internal object FragmentUtility {
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
