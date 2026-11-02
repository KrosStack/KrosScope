package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.engine.detector.CoreArtifactTypes
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetRole

/**
 * The sources a module compiles into its JAR, as stems: the path of a Kotlin or Java file below the source root that
 * holds it, without the extension. Only the main source sets are read; tests do not go into the JAR.
 */
internal class ProducerStems(sourceSets: List<SourceSet>, artifacts: List<Artifact>) {
    private val mainSourceSets = sourceSets.filter { it.role == SourceSetRole.MAIN }.associateBy { it.id }
    private val byModule: Map<ModuleId, Set<String>> = artifacts
        .filter { it.type.id in sourceTypes }
        .mapNotNull { artifact -> artifact.sourceSet?.let(mainSourceSets::get)?.let { stemOf(artifact, it) } }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.toSet() }

    fun of(module: ModuleId): Set<String> = byModule[module].orEmpty()

    private fun stemOf(artifact: Artifact, sourceSet: SourceSet): Pair<ModuleId, String>? {
        val path = artifact.ref.path.value
        val below = sourceSet.roots.mapNotNull { below(it.directory.value, path) }.minByOrNull(String::length)
        return below?.let { sourceSet.module to it.substringBeforeLast('.') }
    }

    private fun below(root: String, path: String): String? = when {
        root.isEmpty() -> path
        path.startsWith("$root/") -> path.removePrefix("$root/")
        else -> null
    }

    private companion object {
        val sourceTypes = setOf(CoreArtifactTypes.KOTLIN_SOURCE, CoreArtifactTypes.JAVA_SOURCE)
    }
}
