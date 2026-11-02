package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.VariantId

/** The `dependsOn` closure and the variants of the source sets of a workspace. */
internal class SourceSetModel(sourceSets: List<SourceSet>) {
    private val byId: Map<SourceSetId, SourceSet> = sourceSets.associateBy { it.id }
    private val closures: Map<SourceSetId, Set<SourceSetId>> = sourceSets.associate { it.id to closureOf(it.id) }

    operator fun get(id: SourceSetId): SourceSet? = byId[id]

    /** True when the build model states the source set with exact certainty. */
    fun isExact(id: SourceSetId?): Boolean = id?.let { byId[it] }?.certainty == Certainty.EXACT

    /** True when [dependent] reaches [base] through `dependsOn` edges, as `iosMain` reaches `commonMain`. */
    fun dependsOn(dependent: SourceSetId, base: SourceSetId): Boolean = base in closures[dependent].orEmpty()

    fun isRelated(first: SourceSetId, second: SourceSetId): Boolean =
        first == second || dependsOn(first, second) || dependsOn(second, first)

    /** True when both source sets belong to variants and have none in common, as `free` and `paid` do. */
    fun areAlternatives(first: SourceSetId, second: SourceSetId): Boolean {
        val firstVariants = variantsOf(first)
        val secondVariants = variantsOf(second)
        val isDisjoint = firstVariants.none { it in secondVariants }
        return firstVariants.isNotEmpty() && secondVariants.isNotEmpty() && isDisjoint && !isRelated(first, second)
    }

    private fun variantsOf(id: SourceSetId): List<VariantId> = byId[id]?.variants.orEmpty()

    private fun closureOf(start: SourceSetId): Set<SourceSetId> {
        val reached = linkedSetOf<SourceSetId>()
        val pending = ArrayDeque(byId[start]?.dependsOn.orEmpty())
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (reached.add(next)) pending.addAll(byId[next]?.dependsOn.orEmpty())
        }
        return reached
    }
}
