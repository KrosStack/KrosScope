package io.github.krosstack.kscope.context.selection.generated

import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.context.fragment.contextNodeKey
import io.github.krosstack.kscope.context.selection.IncrementalChoice
import io.github.krosstack.kscope.context.selection.legacy.LegacyContextSelector
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal data class CaseFacts(
    val offers: Int,
    val repositories: Int,
    val modulesOutOfPathOrder: Boolean,
    val sameModuleAndPath: Boolean,
    val sameNodeKey: Boolean,
    val loneSurrogates: Boolean,
    val unresolved: Int,
    val threeVariants: Boolean,
    val duplicatedOffers: Boolean,
    val generatedSurface: Boolean,
    val testRole: Boolean,
    val mergedFragments: Boolean,
    val bindingBudget: Boolean,
    val manyOffersInFile: Boolean,
    val exhaustedEarly: Boolean,
    val twinFiles: Boolean,
    val zeroValue: Boolean,
    val zeroValueKeyCollision: Boolean,
    val absorbedFile: Boolean,
)

internal fun caseFactsOf(case: GeneratedSelection, estimator: TokenEstimator): CaseFacts {
    val offers = case.offers(ContextLevel.RECOMMENDED, estimator)
    val fragments = offers.flatMap { it.variants }
    val nodes = offers.map { it.variants.first().node }.distinct()
    val last = offers.distinctBy { it.variants.first().node }.map { it.variants.last() }
    val artifacts = case.files.map { it.artifact.ref }
    val order = IncrementalChoice.fileOrder(case.graph)
    val total = case.unbudgetedTokens(estimator, offers)
    val half = LegacyContextSelector(case.merge(estimator), estimator, case.graph)
        .select(case.input, offers, TokenCount(maxOf(total / 2, 1)))
    val perFile = offers.groupBy { it.variants.first().artifact }.values
    val zeroNodes = offers.filter { it.variants.last().relevance == 0.0 }.map { it.variants.first().node }.distinct()
    return CaseFacts(
        offers = offers.size,
        repositories = artifacts.map { it.repository }.distinct().size,
        modulesOutOfPathOrder = artifacts.sortedWith(order) != artifacts.sortedBy { it.path.value },
        sameModuleAndPath = !IncrementalChoice.applies(offers, case.graph),
        sameNodeKey = nodes.groupBy(::contextNodeKey).any { it.value.size > 1 },
        loneSurrogates = case.hasLoneSurrogates,
        unresolved = case.input.unresolved.size,
        threeVariants = offers.any { it.variants.size == 3 },
        duplicatedOffers = nodes.size < offers.size,
        generatedSurface = fragments.any { it.rendering == ContextRendering.GENERATED },
        testRole = fragments.any { it.role == ContextRole.TEST },
        mergedFragments = case.merge(estimator).normalize(last).size < last.size,
        bindingBudget = half.omitted.isNotEmpty() && half.fragments.isNotEmpty(),
        manyOffersInFile = perFile.any { it.size >= MANY_OFFERS },
        exhaustedEarly = perFile.any(::exhaustsEarly),
        twinFiles = perFile.map(::valueProfile).filter { it.size >= 2 }.let { it.size != it.toSet().size },
        zeroValue = zeroNodes.isNotEmpty(),
        zeroValueKeyCollision = zeroNodes.groupBy(::contextNodeKey).any { it.value.size > 1 },
        absorbedFile = absorbsDeclarations(half.fragments, offers),
    )
}

private fun exhaustsEarly(file: List<ContextOffer>): Boolean =
    file.any { it.variants.size == 1 } && file.any { it.variants.size > 1 }

private fun absorbsDeclarations(selected: List<ContextFragment>, offers: List<ContextOffer>): Boolean =
    selected.any { fragment -> fragment.rendering == ContextRendering.FILE && offers.none { fragment in it.variants } }

private fun valueProfile(file: List<ContextOffer>): List<Pair<ContextRendering, Double>> = file
    .flatMap { it.variants }.map { it.rendering to it.relevance }
    .sortedWith(compareBy({ it.first }, { it.second }))

private const val MANY_OFFERS = 8
