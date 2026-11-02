package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.context.token.BlockAdditive
import io.github.krosstack.kscope.context.token.BpeBlockBoundary
import io.github.krosstack.kscope.context.token.ExactMarkdownTokens
import io.github.krosstack.kscope.context.token.RenderedMarkdownBlocks
import io.github.krosstack.kscope.model.context.ContextFragment

internal class BlockCost(val measure: Long, val standalone: Boolean)

internal class BlockCosts(private val additive: BlockAdditive, private val blocks: RenderedMarkdownBlocks) {
    private val costs = BoundedCache<String, BlockCost>(MAX_ENTRIES, MAX_CHARACTERS, String::length)

    fun of(fragment: ContextFragment): BlockCost {
        val block = blocks.fragment(fragment)
        return costs.getOrCompute(block) { BlockCost(additive.measure(block), BpeBlockBoundary.standalone(block)) }
    }

    private companion object {
        const val MAX_ENTRIES = 20_000
        const val MAX_CHARACTERS = 16L * 1024 * 1024
    }
}

internal class ExactCounting(
    private val task: String,
    private val blocks: RenderedMarkdownBlocks,
    private val tokens: ExactMarkdownTokens,
) {
    fun tokens(fragments: List<ContextFragment>, unresolvedCount: Int): Int =
        tokens.count(ContextMarkdown.blocks(task, fragments, unresolvedCount, blocks::fragment)).value
}

internal class TokenAccounts(private val tally: TokenTally?, private val exact: ExactCounting) {
    fun tokens(totals: SelectionTotals, files: () -> List<RenderedFile>): Int =
        if (tally != null && totals.irregular == 0) {
            tally.tokens(totals.measure, totals.unresolved)
        } else {
            exact.tokens(files().flatMap { it.fragments }, totals.unresolved)
        }
}
