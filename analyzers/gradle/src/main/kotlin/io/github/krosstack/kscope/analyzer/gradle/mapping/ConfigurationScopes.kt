package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId

/** Maps a dependency configuration, such as `commonMainApi`, to the source set it belongs to. */
internal object ConfigurationScopes {
    private val suffixes = setOf("Api", "Implementation", "CompileOnly", "CompileOnlyApi", "RuntimeOnly")

    fun sourceSetOf(configuration: String, sourceSets: Collection<SourceSet>): SourceSetId? =
        sourceSets.sortedByDescending { it.name.length }.firstOrNull { belongsTo(configuration, it.name) }?.id

    private fun belongsTo(configuration: String, sourceSetName: String): Boolean {
        if (!configuration.startsWith(sourceSetName)) return false
        return configuration.substring(sourceSetName.length) in suffixes
    }
}
