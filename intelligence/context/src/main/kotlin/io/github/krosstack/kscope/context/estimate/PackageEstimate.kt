package io.github.krosstack.kscope.context.estimate

import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.context.TokenCountStore
import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.selection.BlockCosts
import io.github.krosstack.kscope.context.selection.TokenTally
import io.github.krosstack.kscope.context.token.BlockAdditive
import io.github.krosstack.kscope.context.token.PackageTokens
import io.github.krosstack.kscope.context.token.RenderedMarkdownBlocks
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.TokenCount

/**
 * The token total of a package without rendering it. When the estimator is [BlockAdditive], the task block is valid
 * and every fragment block is standalone, the count of the whole text is the sum of the measures of its blocks; in
 * any other case the whole text is counted, exactly as the compiler counts the package it returns.
 */
internal class PackageEstimate(
    graph: ContextGraph,
    private val packages: PackageTokens,
    private val store: TokenCountStore,
) {
    private val locations = graph.locations
    private val additive = packages.estimator as? BlockAdditive
    private val fileKeys = BlockMeasureKeys(graph, packages.estimator.id)
    private val graphKeys = DerivedBlockKeys(graph, packages.estimator.id)

    fun tokens(task: String, fragments: List<ContextFragment>, unresolved: List<Relationship>): TokenCount =
        additiveTokens(task, fragments, unresolved.size)
            ?: packages.count(ContextMarkdown.renderContents(task, fragments, unresolved, locations))

    private fun additiveTokens(task: String, fragments: List<ContextFragment>, unresolvedCount: Int): TokenCount? =
        additive?.let { blockAdditive ->
            val tally = TokenTally(blockAdditive, task)
            val costs = BlockCosts(blockAdditive, RenderedMarkdownBlocks(locations))
            val measures = FragmentMeasures(fileKeys, graphKeys, store, costs)
            standaloneMeasure(fragments, measures)
                ?.takeIf { tally.taskBlockIsValid }
                ?.let { TokenCount(tally.tokens(it, unresolvedCount)) }
        }

    private fun standaloneMeasure(fragments: List<ContextFragment>, measures: FragmentMeasures): Long? {
        var total = 0L
        for (fragment in fragments) {
            val cost = measures.of(fragment)
            if (!cost.standalone) return null
            total += cost.measure
        }
        return total
    }
}
