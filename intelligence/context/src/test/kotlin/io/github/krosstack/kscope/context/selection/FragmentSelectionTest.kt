package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.CharacterEstimator
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.context.estimate.EstimateCase
import io.github.krosstack.kscope.context.estimate.estimateCases
import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.fragment.FragmentMerge
import io.github.krosstack.kscope.context.fragment.FragmentRenderer
import io.github.krosstack.kscope.context.fragment.SourceDocuments
import io.github.krosstack.kscope.context.token.FragmentCosts
import io.github.krosstack.kscope.context.token.TokenCosts
import io.github.krosstack.kscope.context.token.UncountedFragments
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.spi.context.TokenEstimator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class FragmentSelectionTest {
    @Test
    fun `a selection without counts holds the fragments and the omissions of a counting selection`() {
        var fragments = 0
        for (case in estimateCases()) {
            for (estimator in listOf(TokenEstimators.create(), CharacterEstimator())) {
                for (level in ContextLevel.entries) fragments += assertSameSelection(case, estimator, level)
            }
        }

        assertTrue(fragments > 0)
    }

    private fun assertSameSelection(case: EstimateCase, estimator: TokenEstimator, level: ContextLevel): Int {
        val graph = ContextGraph(case.fixture)
        val documents = SourceDocuments(case.fixture)
        val counted = selection(graph, documents, TokenCosts(estimator), estimator).select(case.input, level, null)
        val uncounted = selection(graph, documents, UncountedFragments, estimator).select(case.input, level, null)

        val label = "case=${case.name} estimator=${estimator.id.value} level=$level"
        assertEquals(counted.omitted, uncounted.omitted, label)
        assertTrue(uncounted.fragments.all { it.tokens == TokenCount(0) }, label)
        assertEquals(counted.fragments.map { it.copy(tokens = TokenCount(0)) }, uncounted.fragments, label)
        return counted.fragments.size
    }

    private fun selection(
        graph: ContextGraph,
        documents: SourceDocuments,
        costs: FragmentCosts,
        estimator: TokenEstimator,
    ) = FragmentSelection(
        graph,
        FragmentRenderer(graph, documents, costs),
        FragmentMerge(graph, documents, costs),
        estimator,
    )
}
