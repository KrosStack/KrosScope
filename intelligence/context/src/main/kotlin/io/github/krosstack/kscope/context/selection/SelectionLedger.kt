package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.fragment.FragmentMerge
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextFragment

internal class RenderedFile(
    val fragments: List<ContextFragment>,
    val values: DoubleArray,
    val measure: Long,
    val irregular: Int,
)

internal class FileRendering(private val merge: FragmentMerge, private val costs: BlockCosts?) {
    fun render(artifact: ArtifactRef, entries: List<ContextFragment>): RenderedFile {
        val fragments = merge.normalizeOrdered(artifact, entries)
        val measured = costs?.let { fragments.map(it::of) }
        return RenderedFile(
            fragments,
            DoubleArray(fragments.size) { FragmentUtility.value(fragments[it]) },
            measured?.sumOf { it.measure } ?: 0L,
            measured?.count { !it.standalone } ?: fragments.size,
        )
    }
}

internal data class SelectionTotals(val measure: Long, val irregular: Int, val unresolved: Int) {
    fun replacing(previous: RenderedFile?, replacement: RenderedFile, unresolvedAdded: Int) = SelectionTotals(
        measure - (previous?.measure ?: 0L) + replacement.measure,
        irregular - (previous?.irregular ?: 0) + replacement.irregular,
        unresolved + unresolvedAdded,
    )
}

internal class SelectionBaseline(val tokens: Int, val value: Double)

internal data class SelectionProposal(
    val fragment: ContextFragment,
    val position: Int,
    val tokens: Int,
    val gain: Double,
    val density: Double,
    val nodeKey: String,
) {
    companion object {
        val PREFERENCE: Comparator<SelectionProposal> = compareBy<SelectionProposal> { it.density }
            .thenByDescending { it.nodeKey }
    }
}

/**
 * The selection so far, kept per file: each file's normalized fragments, token measure and values are computed once
 * per change to that file, so judging one more fragment never renders or counts the other files again.
 */
internal class SelectionLedger(
    private val rendering: FileRendering,
    private val accounts: TokenAccounts,
    private val unresolved: UnresolvedCounts,
    private val ranks: FileRanks,
) {
    private val chosen = LinkedHashMap<NodeRef, ContextFragment>()
    private val slots = HashMap<ArtifactRef, FileSlot>()
    private val present = PresentFiles()
    private var totals = SelectionTotals(0L, 0, 0)

    val selected: Map<NodeRef, ContextFragment> get() = chosen

    fun cursors(offers: List<KeyedOffer>): List<OfferCursor> = offers.map { offer ->
        val artifact = offer.variants.first().artifact
        OfferCursor(offer, slots.getOrPut(artifact) { slotOf(artifact) })
    }

    fun baseline(): SelectionBaseline =
        SelectionBaseline(accounts.tokens(totals) { present.rendered() }, present.totalValue())

    fun propose(cursor: OfferCursor, baseline: SelectionBaseline): SelectionProposal {
        val fragment = requireNotNull(cursor.fragment)
        val slot = cursor.slot
        val rendered = renderedProposal(cursor, fragment)
        val tokens = accounts.tokens(totalsWith(slot, rendered)) { present.arranged(slot, rendered) }
        val gain = present.valueWith(slot, rendered) - baseline.value
        return SelectionProposal(
            fragment,
            cursor.position,
            tokens,
            gain,
            gain / (tokens - baseline.tokens).coerceAtLeast(1),
            cursor.nodeKey,
        )
    }

    fun accept(cursor: OfferCursor, proposal: SelectionProposal) {
        val node = proposal.fragment.node
        val slot = cursor.slot
        val rendered = requireNotNull(cursor.currentRendering())
        totals = totalsWith(slot, rendered)
        if (!slot.isPresent) present.add(slot)
        slot.accept(node, proposal.fragment, rendered)
        chosen[node] = proposal.fragment
        cursor.advance()
    }

    private fun slotOf(artifact: ArtifactRef) = FileSlot(artifact, ranks.rankOf(artifact), unresolved.of(artifact))

    private fun renderedProposal(cursor: OfferCursor, fragment: ContextFragment): RenderedFile =
        cursor.currentRendering() ?: cursor.remember(render(cursor.slot, fragment))

    private fun render(slot: FileSlot, fragment: ContextFragment): RenderedFile =
        rendering.render(slot.artifact, slot.entriesWith(fragment.node, fragment))

    private fun totalsWith(slot: FileSlot, rendered: RenderedFile): SelectionTotals =
        totals.replacing(slot.rendered, rendered, if (slot.isPresent) 0 else slot.unresolvedCount)
}
