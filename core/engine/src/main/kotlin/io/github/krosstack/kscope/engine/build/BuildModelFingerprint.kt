package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.engine.detector.CoreArtifactTypes
import io.github.krosstack.kscope.engine.hashing.FingerprintBuilder
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.BuildModelProvider

private const val BUILD_SRC = "buildSrc/"

/**
 * The inputs of a build model provider for one repository: the paths and types of all its files, and the content of
 * those that are build logic, which are build files, scripts, `buildSrc` and the nested builds.
 */
internal class BuildModelFingerprint(private val repository: RepositoryId) {
    fun of(provider: BuildModelProvider, artifacts: List<Artifact>): Fingerprint {
        val nestedBuilds = nestedBuildsOf(artifacts)
        return FingerprintBuilder()
            .add(provider.producer.id)
            .add(provider.producer.version)
            .add(repository.value)
            .addAll(artifacts.flatMap { listOf(it.ref.path.value, it.type.id, buildContentOf(it, nestedBuilds)) })
            .build()
    }

    private fun buildContentOf(artifact: Artifact, nestedBuilds: List<String>) =
        if (isBuildLogic(artifact, nestedBuilds)) artifact.contentHash.value else ""

    private fun isBuildLogic(artifact: Artifact, nestedBuilds: List<String>): Boolean {
        val path = artifact.ref.path.value
        return artifact.type.category == ArtifactCategory.BUILD ||
            artifact.type.id == CoreArtifactTypes.KOTLIN_SCRIPT ||
            path.startsWith(BUILD_SRC) ||
            nestedBuilds.any { path.startsWith(it) }
    }

    private fun nestedBuildsOf(artifacts: List<Artifact>): List<String> = artifacts
        .filter { it.type.id == CoreArtifactTypes.GRADLE_SETTINGS }
        .map { it.ref.path.value.substringBeforeLast('/', "") }
        .filter { it.isNotEmpty() }
        .map { "$it/" }
}
