package io.github.krosstack.kscope.context

import io.github.krosstack.kscope.context.compact.CompactCompiler
import io.github.krosstack.kscope.context.compact.orderedOmissions
import io.github.krosstack.kscope.context.estimate.PackageEstimate
import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.fragment.FragmentMerge
import io.github.krosstack.kscope.context.fragment.FragmentRenderer
import io.github.krosstack.kscope.context.fragment.SourceDocuments
import io.github.krosstack.kscope.context.fragment.contextNodeKey
import io.github.krosstack.kscope.context.fragment.target
import io.github.krosstack.kscope.context.selection.FragmentSelection
import io.github.krosstack.kscope.context.token.PackageTokens
import io.github.krosstack.kscope.context.token.TokenCosts
import io.github.krosstack.kscope.context.token.UncountedFragments
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextOmission
import io.github.krosstack.kscope.model.context.ContextOmissionReason
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.spi.WorkspaceFiles
import io.github.krosstack.kscope.spi.context.TokenEstimator
import io.github.krosstack.kscope.spi.graph.GraphReader

public class ContextCompiler internal constructor(
    graph: GraphReader,
    files: WorkspaceFiles,
    private val estimator: TokenEstimator,
    private val compactBelow: TokenCount,
    tokenCounts: TokenCountStore = TokenCountStore.NONE,
) {
    /** [tokenCounts] keeps the counts of rendered fragments between runs; it never changes a count. */
    public constructor(
        graph: GraphReader,
        files: WorkspaceFiles,
        estimator: TokenEstimator,
        tokenCounts: TokenCountStore = TokenCountStore.NONE,
    ) : this(graph, files, estimator, COMPACT_BELOW, tokenCounts)

    private val graph = ContextGraph(graph)
    private val documents = SourceDocuments(files)
    private val costs = TokenCosts(estimator, tokenCounts)
    private val packages = PackageTokens(estimator, tokenCounts)
    private val renderer = FragmentRenderer(this.graph, documents, costs)
    private val merge = FragmentMerge(this.graph, documents, costs)
    private val compact = CompactCompiler(this.graph, documents, renderer, merge, packages)
    private val counted = selectionOf(renderer, merge)
    private val uncounted = selectionOf(
        FragmentRenderer(this.graph, documents, UncountedFragments),
        FragmentMerge(this.graph, documents, UncountedFragments),
    )
    private val totals = PackageEstimate(this.graph, packages, tokenCounts)

    /**
     * Budgets below 8,000 tokens pack raw source only, in rank order: whole files and declarations, with no
     * signatures, outlines, generated summaries, test signatures or map, and the remainder filled with a line prefix.
     */
    public fun compile(
        input: ContextInput,
        level: ContextLevel = ContextLevel.RECOMMENDED,
        budget: TokenCount? = null,
    ): ContextPackage = if (budget != null && budget.value < compactBelow.value) {
        compact.compile(input, level, budget)
    } else {
        compileWithOffers(input, level, budget)
    }

    /**
     * The total of the package that [compile] returns for [input] and [level] without a budget, equal to it for every
     * input. Where the estimator can add up the counts of blocks, the package is not rendered, and the measure of every
     * fragment block is recalled from, and offered to, the token count store.
     */
    public fun estimate(input: ContextInput, level: ContextLevel): TokenCount {
        val fragments = uncounted.select(input, level, null).fragments
        return totals.tokens(input.task, fragments, unresolvedAmong(input, fragments))
    }

    private fun compileWithOffers(input: ContextInput, level: ContextLevel, budget: TokenCount?): ContextPackage {
        val selection = counted.select(input, level, budget)
        return pack(input, level, budget, selection.fragments, selection.omitted)
    }

    private fun selectionOf(renderer: FragmentRenderer, merge: FragmentMerge) =
        FragmentSelection(graph, renderer, merge, estimator)

    private fun pack(
        input: ContextInput,
        level: ContextLevel,
        budget: TokenCount?,
        fragments: List<ContextFragment>,
        omitted: List<ContextOmission>,
    ): ContextPackage {
        val selected = fragments.map { it.artifact }.toSet()
        val unresolved = unresolvedAmong(input, fragments)
        val full = ContextMarkdown.renderContents(input.task, fragments, unresolved, graph.locations)
        val fits = budget == null || packages.count(full).value <= budget.value
        val text = if (fits) full else ""
        val extra = if (fits) emptyList() else listOf(ContextOmission(null, ContextOmissionReason.BUDGET))
        return ContextPackage(
            input.task, level, budget, estimator.id, packages.count(text), if (fits) fragments else emptyList(),
            boundaries = input.boundaries.filter { related(it, selected) },
            tests = input.tests.filter { graph.artifact(it) in selected }.distinct().sortedBy(::contextNodeKey),
            unresolved = unresolved,
            omitted = orderedOmissions(omitted + extra),
            markdown = text,
        )
    }

    private fun unresolvedAmong(input: ContextInput, fragments: List<ContextFragment>): List<Relationship> {
        val selected = fragments.map { it.artifact }.toSet()
        return input.unresolved.filter { graph.artifact(it.from) in selected }
    }

    private fun related(edge: Relationship, files: Set<ArtifactRef>): Boolean =
        graph.artifact(edge.from) in files || edge.target()?.let(graph::artifact) in files

    private companion object {
        const val COMPACT_BELOW_TOKENS = 8_000
        val COMPACT_BELOW = TokenCount(COMPACT_BELOW_TOKENS)
    }
}
