package io.github.krosstack.kscope.analyzer.gradle.tooling

import io.github.krosstack.kscope.analyzer.gradle.ArtifactTypes
import io.github.krosstack.kscope.analyzer.gradle.SUPPORTED_SCHEMA
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.RepositoryId
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Fingerprints everything that can change the fetched model: settings and build scripts, property files, version
 * catalogs, the sources of included builds and `buildSrc`, the layout of the directories, the wrapper, the Java
 * runtime, the plugin that is injected and the schema it speaks. The repository and the location of its checkout
 * are part of it as well, because the fetched documents hold absolute paths of that checkout.
 */
internal class BuildInputs(
    private val repository: RepositoryId,
    private val workspaceRoot: Path,
    private val gradleUserHome: Path,
    private val gradleJavaHome: Path? = null,
) {
    fun fingerprint(producer: ProducerRef, artifacts: List<Artifact>, assets: PluginAssets): String {
        val nestedBuilds = artifacts.filter { it.type.id == ArtifactTypes.GRADLE_SETTINGS }
            .map { parentOf(it.ref.path.value) }
            .filter { it.isNotEmpty() }
        val buildInputs = artifacts.filter { affectsModel(it, nestedBuilds) }.sortedBy { it.ref.path.value }
        val layout = artifacts.map { parentOf(it.ref.path.value) }.distinct().sorted()
        val parts = listOf(producer.id, producer.version, SCHEMA.toString(), assets.hash, runtime()) +
            listOf(repository.value, checkoutLocation()) +
            buildInputs.flatMap { listOf(it.ref.path.value, it.contentHash.value) } +
            layout +
            environmentFiles().flatMap { (name, content) -> listOf(name, content) }
        return digest(parts)
    }

    private fun affectsModel(artifact: Artifact, nestedBuilds: List<String>): Boolean {
        val path = artifact.ref.path.value
        return artifact.type.category == ArtifactCategory.BUILD ||
            artifact.type.id == ArtifactTypes.KOTLIN_SCRIPT ||
            path.startsWith("$BUILD_SRC/") ||
            nestedBuilds.any { path.startsWith("$it/") }
    }

    private fun environmentFiles(): List<Pair<String, String>> = listOf(
        workspaceRoot.resolve("gradle/wrapper/gradle-wrapper.properties"),
        workspaceRoot.resolve("local.properties"),
        gradleUserHome.resolve("gradle.properties"),
    ).map { file -> file.fileName.toString() to contentHash(file) }

    private fun contentHash(file: Path): String =
        if (Files.isRegularFile(file)) digest(listOf(Files.readString(file))) else ""

    private fun runtime(): String {
        val tool = "${System.getProperty("java.version")}/${System.getProperty("java.vendor")}"
        return gradleJavaHome?.let { "$tool/$it/${contentHash(it.resolve(RELEASE_FILE))}" } ?: tool
    }

    private fun checkoutLocation(): String = workspaceRoot.toAbsolutePath().normalize().toString()

    private fun parentOf(path: String): String = path.substringBeforeLast('/', "")

    private fun digest(parts: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { part ->
            val bytes = part.toByteArray(Charsets.UTF_8)
            digest.update(bytes.size.toString().toByteArray(Charsets.US_ASCII))
            digest.update(SEPARATOR)
            digest.update(bytes)
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    private companion object {
        const val SCHEMA = SUPPORTED_SCHEMA
        const val BUILD_SRC = "buildSrc"
        const val RELEASE_FILE = "release"
        const val SEPARATOR: Byte = 0
    }
}
