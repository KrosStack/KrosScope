package io.github.krosstack.kscope.context.compact

import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.context.fragment.FragmentMerge
import io.github.krosstack.kscope.context.token.ExactMarkdownTokens
import io.github.krosstack.kscope.context.token.RenderedMarkdownBlocks
import io.github.krosstack.kscope.model.WorkspaceLocations
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextOmission
import io.github.krosstack.kscope.model.context.ContextOmissionReason
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal data class CompactSelection(val fragments: List<ContextFragment>, val omitted: List<ContextOmission>)

internal class CompactPacker(
    private val merge: FragmentMerge,
    private val estimator: TokenEstimator,
    locations: WorkspaceLocations = WorkspaceLocations.SINGLE,
) {
    private val tokens = ExactMarkdownTokens(estimator)
    private val blocks = RenderedMarkdownBlocks(locations)

    fun pack(task: String, ranked: List<ContextFragment>, budget: TokenCount): CompactSelection {
        val fit = Fit(task, budget)
        val packing = ranked.fold(Packing()) { packing, fragment ->
            val next = merge.normalize(packing.packed + fragment)
            if (fit(next)) packing.copy(packed = next) else packing.copy(skipped = packing.skipped + fragment)
        }
        val represented = packing.packed.map { it.artifact }.toSet()
        val head = packing.skipped.firstOrNull { it.artifact !in represented }
        val prefix = head?.let { prefix(it, packing.packed, fit) }
        val packed = if (prefix == null) packing.packed else merge.normalize(packing.packed + prefix)
        val omitted = packing.skipped.filter { it != head || prefix == null }
            .map { ContextOmission(it.node, ContextOmissionReason.BUDGET) }
        return CompactSelection(packed, omitted)
    }

    private fun prefix(fragment: ContextFragment, packed: List<ContextFragment>, fit: Fit): ContextFragment? {
        val cuts = LineCuts(fragment)
        var fitting = 0
        var limit = cuts.count
        while (fitting < limit) {
            val middle = (fitting + limit + 1) / 2
            if (fit(merge.normalize(packed + cuts.prefix(middle, estimator)))) fitting = middle else limit = middle - 1
        }
        return if (fitting == 0) null else cuts.prefix(fitting, estimator).takeIf { it.text.isNotBlank() }
    }

    private inner class Fit(private val task: String, private val budget: TokenCount) {
        operator fun invoke(fragments: List<ContextFragment>): Boolean =
            tokens.count(ContextMarkdown.blocks(task, fragments, emptyList(), blocks::fragment)).value <= budget.value
    }

    private data class Packing(
        val packed: List<ContextFragment> = emptyList(),
        val skipped: List<ContextFragment> = emptyList(),
    )
}
