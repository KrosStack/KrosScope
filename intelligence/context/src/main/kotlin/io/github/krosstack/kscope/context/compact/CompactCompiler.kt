package io.github.krosstack.kscope.context.compact

import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.fragment.ContextPlanner
import io.github.krosstack.kscope.context.fragment.FragmentMerge
import io.github.krosstack.kscope.context.fragment.FragmentRenderer
import io.github.krosstack.kscope.context.fragment.SourceDocuments
import io.github.krosstack.kscope.context.fragment.contextNodeKey
import io.github.krosstack.kscope.context.token.PackageTokens
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextOmission
import io.github.krosstack.kscope.model.context.ContextOmissionReason
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount

internal class CompactCompiler(
    graph: ContextGraph,
    documents: SourceDocuments,
    renderer: FragmentRenderer,
    merge: FragmentMerge,
    private val packages: PackageTokens,
) {
    private val locations = graph.locations
    private val planner = ContextPlanner(graph)
    private val sources = CompactSources(graph, documents, renderer)
    private val packer = CompactPacker(merge, packages.estimator, locations)

    fun compile(input: ContextInput, level: ContextLevel, budget: TokenCount): ContextPackage {
        val plan = planner.ranked(input, level)
        val raw = plan.candidates.map(sources::of)
        val ranked = raw.filterIsInstance<RawSource.Ready>().map { it.fragment }
        val selection = packer.pack(input.task, ranked, budget)
        val dropped = raw.filterIsInstance<RawSource.Dropped>().map { it.omission }
        return pack(input, level, budget, selection.fragments, plan.omitted + dropped + selection.omitted)
    }

    private fun pack(
        input: ContextInput,
        level: ContextLevel,
        budget: TokenCount,
        fragments: List<ContextFragment>,
        omitted: List<ContextOmission>,
    ): ContextPackage {
        val full = ContextMarkdown.renderContents(input.task, fragments, locations = locations)
        val fits = packages.count(full).value <= budget.value
        val text = if (fits) full else ""
        val extra = if (fits) emptyList() else listOf(ContextOmission(null, ContextOmissionReason.BUDGET))
        return ContextPackage(
            input.task,
            level,
            budget,
            packages.estimator.id,
            packages.count(text),
            if (fits) fragments else emptyList(),
            omitted = orderedOmissions(omitted + extra),
            markdown = text,
        )
    }
}

internal fun orderedOmissions(omissions: List<ContextOmission>): List<ContextOmission> =
    omissions.distinct().sortedWith(
        compareBy<ContextOmission> { it.node?.let(::contextNodeKey).orEmpty() }.thenBy { it.reason },
    )
