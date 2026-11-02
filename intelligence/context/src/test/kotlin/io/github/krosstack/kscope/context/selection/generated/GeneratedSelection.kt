package io.github.krosstack.kscope.context.selection.generated

import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.context.fragment.ContextPlanner
import io.github.krosstack.kscope.context.fragment.FragmentMerge
import io.github.krosstack.kscope.context.fragment.FragmentRenderer
import io.github.krosstack.kscope.context.fragment.SourceDocuments
import io.github.krosstack.kscope.context.selection.legacy.LegacyContextSelector
import io.github.krosstack.kscope.context.token.TokenCosts
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.spi.context.TokenEstimator
import kotlin.random.Random

internal class GeneratedSelection(
    val seed: Long,
    val fixture: StructuredFixture,
    val input: ContextInput,
    val files: List<GeneratedFile>,
    val profile: CaseProfile = CaseProfile.ORDINARY,
) {
    val graph = ContextGraph(fixture)

    val hasLoneSurrogates: Boolean = input.task.hasLoneSurrogate() ||
        graph.symbols.values.any { it.signature?.hasLoneSurrogate() == true }

    fun offers(level: ContextLevel, estimator: TokenEstimator): List<ContextOffer> {
        val renderer = FragmentRenderer(graph, SourceDocuments(fixture), TokenCosts(estimator))
        val rendered = ContextPlanner(graph).plan(input, level).candidates.flatMap { renderer.render(it, level).offers }
        return OfferVariation(Random(seed * LEVELS + level.ordinal), profile).vary(rendered)
    }

    fun merge(estimator: TokenEstimator): FragmentMerge =
        FragmentMerge(graph, SourceDocuments(fixture), TokenCosts(estimator))

    fun unbudgetedTokens(estimator: TokenEstimator, offers: List<ContextOffer>): Int {
        val fragments = LegacyContextSelector(merge(estimator), estimator, graph).select(input, offers, null).fragments
        val selected = fragments.map { it.artifact }.toSet()
        val unresolved = input.unresolved.filter { graph.artifact(it.from) in selected }
        return estimator.count(ContextMarkdown.renderContents(input.task, fragments, unresolved)).value
    }

    private companion object {
        val LEVELS = ContextLevel.entries.size.toLong()
    }
}

private fun String.hasLoneSurrogate(): Boolean = withIndex().any { (index, character) ->
    when {
        character.isHighSurrogate() -> getOrNull(index + 1)?.isLowSurrogate() != true
        character.isLowSurrogate() -> getOrNull(index - 1)?.isHighSurrogate() != true
        else -> false
    }
}
