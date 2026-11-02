package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.analyzer.gradle.ArtifactTypes
import io.github.krosstack.kscope.analyzer.gradle.workspace.RepoPaths
import io.github.krosstack.kscope.model.Artifact

/** The artifacts a provider run reads, by type. */
internal class ProviderInputs(private val artifacts: List<Artifact>) {
    private fun ofType(id: String) = artifacts.filter { it.type.id == id }.sortedBy { it.ref.path.value }

    val settings: List<Artifact> = ofType(ArtifactTypes.GRADLE_SETTINGS)
    val buildScripts: List<Artifact> = ofType(ArtifactTypes.GRADLE_BUILD_SCRIPT)
    val catalogs: List<Artifact> = ofType(ArtifactTypes.GRADLE_VERSION_CATALOG)
    val properties: List<Artifact> = ofType(ArtifactTypes.GRADLE_PROPERTIES)
    val kotlinScripts: List<Artifact> = ofType(ArtifactTypes.KOTLIN_SCRIPT)
    val kotlinSources: List<Artifact> = ofType(ArtifactTypes.KOTLIN_SOURCE)

    fun directoryOf(artifact: Artifact): String = RepoPaths.parentOf(artifact.ref.path.value)

    fun scriptIn(directory: String): Artifact? = buildScripts.firstOrNull { directoryOf(it) == directory }

    fun settingsIn(directory: String): Artifact? = settings.firstOrNull { directoryOf(it) == directory }
}
