package io.github.krosstack.kscope.context.selection.legacy

import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.context.fragment.FragmentMerge
import io.github.krosstack.kscope.context.fragment.contextNodeKey
import io.github.krosstack.kscope.context.selection.BlockCosts
import io.github.krosstack.kscope.context.selection.ContextSelection
import io.github.krosstack.kscope.context.selection.ExactCounting
import io.github.krosstack.kscope.context.selection.FileRendering
import io.github.krosstack.kscope.context.selection.RescanChoice
import io.github.krosstack.kscope.context.selection.TokenAccounts
import io.github.krosstack.kscope.context.selection.TokenTally
import io.github.krosstack.kscope.context.selection.UnresolvedCounts
import io.github.krosstack.kscope.context.token.BlockAdditive
import io.github.krosstack.kscope.context.token.ExactMarkdownTokens
import io.github.krosstack.kscope.context.token.RenderedMarkdownBlocks
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextOmission
import io.github.krosstack.kscope.model.context.ContextOmissionReason
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal class LegacyContextSelector(
    private val merge: FragmentMerge,
    private val estimator: TokenEstimator,
    private val graph: ContextGraph,
    private val fileOrder: Comparator<ArtifactRef> = LegacyIncrementalChoice.fileOrder(graph),
) {
    private val tokens = ExactMarkdownTokens(estimator)
    private val blocks = RenderedMarkdownBlocks()

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
        val unresolved = UnresolvedCounts(input.unresolved, graph)
        val counting = ExactCounting(input.task, blocks, tokens)
        if (!LegacyIncrementalChoice.applies(offers, graph)) {
            return RescanChoice(offers, budget, merge, counting, unresolved).choose()
        }
        val additive = estimator as? BlockAdditive
        val tally = additive?.let { TokenTally(it, input.task) }?.takeIf { it.taskBlockIsValid }
        val costs = additive?.takeIf { tally != null }?.let { BlockCosts(it, blocks) }
        val ledger = LegacySelectionLedger(
            FileRendering(merge, costs),
            TokenAccounts(tally, counting),
            unresolved,
            fileOrder,
        )
        return LegacyIncrementalChoice(offers, budget, ledger).choose()
    }
}
