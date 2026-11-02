package io.github.krosstack.kscope.context.selection.legacy

import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.context.TokenCountStore
import io.github.krosstack.kscope.context.compact.orderedOmissions
import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.fragment.ContextPlanner
import io.github.krosstack.kscope.context.fragment.FragmentMerge
import io.github.krosstack.kscope.context.fragment.FragmentRenderer
import io.github.krosstack.kscope.context.fragment.SourceDocuments
import io.github.krosstack.kscope.context.fragment.contextNodeKey
import io.github.krosstack.kscope.context.fragment.target
import io.github.krosstack.kscope.context.token.PackageTokens
import io.github.krosstack.kscope.context.token.TokenCosts
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

/**
 * The offer-based path of the compiler wired to the legacy selector, so that whole packages can be compared with
 * the ones the current compiler builds.
 */
internal class LegacyContextCompiler(
    reader: GraphReader,
    files: WorkspaceFiles,
    private val estimator: TokenEstimator,
) {
    private val graph = ContextGraph(reader)
    private val documents = SourceDocuments(files)
    private val costs = TokenCosts(estimator)
    private val packages = PackageTokens(estimator, TokenCountStore.NONE)
    private val renderer = FragmentRenderer(graph, documents, costs)
    private val merge = FragmentMerge(graph, documents, costs)

    fun compile(input: ContextInput, level: ContextLevel, budget: TokenCount?): ContextPackage {
        val plan = ContextPlanner(graph).plan(input, level)
        val alternatives = plan.candidates.map { renderer.render(it, level) }
        val selection = LegacyContextSelector(merge, estimator, graph)
            .select(input, alternatives.flatMap { it.offers }, budget)
        val omissions = plan.omitted + alternatives.flatMap { it.omitted } + selection.omitted
        return pack(input, level, budget, selection.fragments, omissions)
    }

    private fun pack(
        input: ContextInput,
        level: ContextLevel,
        budget: TokenCount?,
        fragments: List<ContextFragment>,
        omitted: List<ContextOmission>,
    ): ContextPackage {
        val selected = fragments.map { it.artifact }.toSet()
        val unresolved = input.unresolved.filter { graph.artifact(it.from) in selected }
        val full = ContextMarkdown.renderContents(input.task, fragments, unresolved)
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

    private fun related(edge: Relationship, files: Set<ArtifactRef>): Boolean =
        graph.artifact(edge.from) in files || edge.target()?.let(graph::artifact) in files
}
