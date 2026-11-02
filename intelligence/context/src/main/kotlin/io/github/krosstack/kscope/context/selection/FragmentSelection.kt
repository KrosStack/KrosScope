package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.fragment.ContextPlanner
import io.github.krosstack.kscope.context.fragment.FragmentMerge
import io.github.krosstack.kscope.context.fragment.FragmentRenderer
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.spi.context.TokenEstimator

/** Plans, renders and selects the fragments of a package; the compiler and its estimate both go through it. */
internal class FragmentSelection(
    private val graph: ContextGraph,
    private val renderer: FragmentRenderer,
    private val merge: FragmentMerge,
    private val estimator: TokenEstimator,
) {
    fun select(input: ContextInput, level: ContextLevel, budget: TokenCount?): ContextSelection {
        val plan = ContextPlanner(graph).plan(input, level)
        val alternatives = plan.candidates.map { renderer.render(it, level) }
        val selection = ContextSelector(merge, estimator, graph)
            .select(input, alternatives.flatMap { it.offers }, budget)
        return ContextSelection(
            selection.fragments,
            plan.omitted + alternatives.flatMap { it.omitted } + selection.omitted,
        )
    }
}
