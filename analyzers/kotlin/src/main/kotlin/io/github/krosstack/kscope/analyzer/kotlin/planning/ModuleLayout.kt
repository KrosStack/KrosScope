package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.SourceSetId

/** The analysis modules of one Gradle module, and the default variant group among them. */
internal class ModuleLayout(val groups: List<SourceSetGroup>, val defaultGroup: SourceSetGroup?) {
    fun groupOf(id: SourceSetId): SourceSetGroup? = groups.firstOrNull { id in it.ids }

    fun groupContaining(sourceSetName: String): SourceSetGroup? = groups.firstOrNull { it.contains(sourceSetName) }

    fun defaultOrMain(): SourceSetGroup? = defaultGroup ?: groupContaining(MAIN)

    fun commonMain(): SourceSetGroup? = groupContaining(COMMON_MAIN) ?: defaultOrMain()

    /** A variant source set outside the default group, such as `release`, is not offered to other modules. */
    fun isOutsideDefaultVariant(group: SourceSetGroup): Boolean =
        group.isMainSide && group !== defaultGroup && group.members.any { it.variants.isNotEmpty() }

    /** What a dependent module can see of this one: the default group and the main source sets without variants. */
    fun offered(): List<SourceSetGroup> = groups.filter { it.isMainSide && !isOutsideDefaultVariant(it) }

    private companion object {
        const val MAIN = "main"
        const val COMMON_MAIN = "commonMain"
    }
}
