package io.github.krosstack.kscope.context.selection.legacy

import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.context.fragment.contextNodeKey
import io.github.krosstack.kscope.context.selection.FileRendering
import io.github.krosstack.kscope.context.selection.RenderedFile
import io.github.krosstack.kscope.context.selection.SelectionBaseline
import io.github.krosstack.kscope.context.selection.SelectionTotals
import io.github.krosstack.kscope.context.selection.TokenAccounts
import io.github.krosstack.kscope.context.selection.UnresolvedCounts
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextFragment

internal data class LegacySelectionProposal(
    val fragment: ContextFragment,
    val position: Int,
    val tokens: Int,
    val gain: Double,
    val density: Double,
) {
    companion object {
        val PREFERENCE: Comparator<LegacySelectionProposal> = compareBy<LegacySelectionProposal> { it.density }
            .thenByDescending { contextNodeKey(it.fragment.node) }
    }
}

/**
 * The ledger as it was before files were ordered by a precomputed rank, kept as the reference that the equivalence
 * tests compare the current ledger with.
 */
internal class LegacySelectionLedger(
    private val rendering: FileRendering,
    private val accounts: TokenAccounts,
    private val unresolved: UnresolvedCounts,
    private val fileOrder: Comparator<ArtifactRef>,
) {
    private val chosen = LinkedHashMap<NodeRef, ContextFragment>()
    private val positions = HashMap<NodeRef, Int>()
    private val slots = HashMap<ArtifactRef, LegacyFileSlot>()
    private val present = ArrayList<LegacyFileSlot>()
    private var totals = SelectionTotals(0L, 0, 0)

    val selected: Map<NodeRef, ContextFragment> get() = chosen

    fun baseline(): SelectionBaseline {
        val files = presentFiles()
        return SelectionBaseline(accounts.tokens(totals) { files }, valueOf(files))
    }

    fun propose(offer: ContextOffer, baseline: SelectionBaseline): LegacySelectionProposal? {
        val node = offer.variants.first().node
        val position = (positions[node] ?: -1) + 1
        val fragment = offer.variants.getOrNull(position) ?: return null
        val slot = slots.getOrPut(fragment.artifact) { LegacyFileSlot(fragment.artifact) }
        val rendered = renderedProposal(slot, fragment)
        val files = arranged(slot, rendered)
        val tokens = accounts.tokens(totalsWith(slot, rendered)) { files }
        val gain = valueOf(files) - baseline.value
        return LegacySelectionProposal(
            fragment,
            position,
            tokens,
            gain,
            gain / (tokens - baseline.tokens).coerceAtLeast(1),
        )
    }

    fun accept(proposal: LegacySelectionProposal) {
        val node = proposal.fragment.node
        val slot = slots.getValue(proposal.fragment.artifact)
        val rendered = slot.proposals.getValue(node)
        totals = totalsWith(slot, rendered)
        if (!slot.isPresent) present.add(insertionIndex(slot), slot)
        slot.accept(node, proposal.fragment, rendered)
        chosen[node] = proposal.fragment
        positions[node] = proposal.position
    }

    private fun renderedProposal(slot: LegacyFileSlot, fragment: ContextFragment): RenderedFile =
        slot.proposals.getOrPut(fragment.node) {
            rendering.render(slot.artifact, slot.entriesWith(fragment.node, fragment))
        }

    private fun totalsWith(slot: LegacyFileSlot, rendered: RenderedFile): SelectionTotals =
        totals.replacing(slot.rendered, rendered, if (slot.isPresent) 0 else unresolved.of(slot.artifact))

    private fun arranged(slot: LegacyFileSlot, rendered: RenderedFile): List<RenderedFile> {
        val files = presentFiles().toMutableList()
        val index = insertionIndex(slot)
        if (slot.isPresent) files[index] = rendered else files.add(index, rendered)
        return files
    }

    private fun presentFiles(): List<RenderedFile> = present.map { requireNotNull(it.rendered) }

    private fun insertionIndex(slot: LegacyFileSlot): Int {
        val found = present.binarySearch { fileOrder.compare(it.artifact, slot.artifact) }
        return if (found >= 0) found else -(found + 1)
    }

    private fun valueOf(files: List<RenderedFile>): Double {
        var sum = 0.0
        for (file in files) for (value in file.values) sum += value
        return sum
    }
}
