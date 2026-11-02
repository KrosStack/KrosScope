package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.Target

internal data class Publication(val module: Module, val group: String, val artifact: String, val version: String)

/**
 * The coordinates that modules publish, found by group and artifact. For each root publication of a Kotlin
 * Multiplatform module that has no target publication among the entries of the module, the artifact id of each target
 * is found as well.
 */
internal class PublicationIndex(modules: List<Module>, targets: List<Target>) {
    private val byCoordinate: Map<Pair<String, String>, List<Publication>>

    init {
        val targetNames = targets.groupBy({ it.module }, { it.name.lowercase() })
        byCoordinate = modules.flatMap { module ->
            val declared = publicationsOf(module)
            val aliases = if (ModuleTrait.KOTLIN_MULTIPLATFORM in module.traits) {
                aliasesOf(declared, targetNames[module.id].orEmpty().sorted())
            } else {
                emptyList()
            }
            declared + aliases
        }.groupBy { it.group to it.artifact }
    }

    fun find(group: String, artifact: String): List<Publication> = byCoordinate[group to artifact].orEmpty()

    private fun aliasesOf(publications: List<Publication>, targets: List<String>): List<Publication> {
        val declared = publications.map(::coordinateOf).toSet()
        val derived = publications.associateWith { targetPublicationsOf(it, targets) }
        val publishedTargets = derived.values.flatten().map(::coordinateOf).filter { it in declared }.toSet()
        return derived
            .filterKeys { coordinateOf(it) !in publishedTargets }
            .values
            .filter { aliases -> aliases.none { coordinateOf(it) in declared } }
            .flatten()
    }

    private fun targetPublicationsOf(root: Publication, targets: List<String>) =
        targets.map { root.copy(artifact = "${root.artifact}-$it") }

    private fun coordinateOf(publication: Publication) = publication.group to publication.artifact

    private fun publicationsOf(module: Module): List<Publication> =
        module.attributes[ATTRIBUTE].orEmpty().split(',').mapNotNull { entry ->
            val parts = entry.trim().split(':')
            if (parts.size == COORDINATE_PARTS && parts.none(String::isEmpty)) {
                Publication(module, parts[0], parts[1], parts[2])
            } else {
                null
            }
        }

    private companion object {
        const val ATTRIBUTE = "maven.publications"
        const val COORDINATE_PARTS = 3
    }
}
