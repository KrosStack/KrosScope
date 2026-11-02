package io.github.krosstack.kscope.analyzer.android.linking

import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.Variant

private const val MAIN_RANK = 0
private const val FLAVOR_RANK = 1
private const val BUILD_TYPE_RANK = 2
private const val VARIANT_RANK = 3

/**
 * Which declaration wins among the source sets of one module: a variant source set over a build type over a flavor
 * over `main`, as Android merges them. A declaration in a source set that applies to the variants of the referencing
 * source set is chosen over one that applies to some variants only.
 */
internal class SourcePrecedence(sourceSets: List<SourceSet>, variants: List<Variant>) {
    private val sourceSetsById = sourceSets.associateBy { it.id }
    private val variantsByModule = variants.groupBy { it.module }

    /** The most fitting of [candidates] for a reference made from [from]; the candidates must not be empty. */
    fun best(candidates: List<LinkCandidate>, from: SourceSetId?): LinkCandidate {
        val origin = from?.let(sourceSetsById::get)
        val (visible, hidden) = candidates.partition { applies(it.sourceSet, origin) }
        val strongestFirst = compareByDescending<LinkCandidate> { rankOf(it.sourceSet) }.thenBy { it.symbolId.value }
        val weakestFirst = compareBy<LinkCandidate> { rankOf(it.sourceSet) }.thenBy { it.symbolId.value }
        return visible.sortedWith(strongestFirst).firstOrNull() ?: hidden.sortedWith(weakestFirst).first()
    }

    private fun applies(definition: SourceSetId?, origin: SourceSet?): Boolean {
        val defining = definition?.let(sourceSetsById::get) ?: return false
        return when {
            origin == null -> rankOf(definition) == MAIN_RANK

            origin.variants.isNotEmpty() && defining.variants.isNotEmpty() ->
                defining.variants.containsAll(origin.variants)

            else -> defining.id == origin.id || rankOf(definition) == MAIN_RANK
        }
    }

    private fun rankOf(id: SourceSetId?): Int {
        val sourceSet = id?.let(sourceSetsById::get) ?: return MAIN_RANK
        val variants = variantsByModule[sourceSet.module].orEmpty()
        return when {
            variants.any { it.name == sourceSet.name } -> VARIANT_RANK
            variants.any { it.buildType == sourceSet.name } -> BUILD_TYPE_RANK
            variants.any { variant -> variant.flavors.any { sourceSet.name.startsWith(it) } } -> FLAVOR_RANK
            else -> MAIN_RANK
        }
    }
}
