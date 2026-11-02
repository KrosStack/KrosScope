package io.github.krosstack.kscope.analyzer.gradle.statics.android

import io.github.krosstack.kscope.analyzer.gradle.statics.layout.VariantDraft
import io.github.krosstack.kscope.analyzer.gradle.statics.layout.upperFirst

/** The build variants of an Android project: every combination of one flavor per dimension and a build type. */
internal object AndroidVariants {
    fun of(config: AndroidConfig): List<VariantDraft> {
        val combinations = flavorsByDimension(config).fold(listOf(emptyList<String>())) { prefixes, group ->
            prefixes.flatMap { prefix -> group.map { prefix + it.name } }
        }
        return combinations.flatMap { flavors ->
            config.buildTypes.map { buildType -> VariantDraft(nameOf(flavors, buildType), buildType, flavors) }
        }
    }

    /** The names of the source sets a variant is assembled from, besides `main`. */
    fun constituents(variant: VariantDraft): List<String> {
        val combination = nameOf(variant.flavors, "").takeIf { variant.flavors.size > 1 }
        return variant.flavors + listOfNotNull(combination) + variant.buildType + variant.name
    }

    private fun flavorsByDimension(config: AndroidConfig): List<List<Flavor>> {
        val dimensions = config.dimensions.ifEmpty { listOf("") }
        val single = dimensions.singleOrNull()
        return dimensions.map { dimension ->
            config.flavors.filter { (it.dimension ?: single) == dimension }
        }.filter { it.isNotEmpty() }
    }

    private fun nameOf(flavors: List<String>, buildType: String): String {
        val prefix = flavors.mapIndexed { index, flavor ->
            if (index == 0) flavor else flavor.upperFirst()
        }.joinToString("")
        return if (prefix.isEmpty()) buildType else prefix + buildType.upperFirst()
    }
}
