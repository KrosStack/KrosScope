package io.github.krosstack.kscope.analyzer.gradle

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.BuildModelRequest
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/** Builds the request the engine would hand to a build model provider, from the files of a directory. */
internal object WorkspaceRequests {
    private val skippedDirectories = setOf("build", ".gradle", ".git", ".kscope", ".idea", ".kotlin")

    private val buildScripts = setOf("build.gradle", "build.gradle.kts")
    private val settingsScripts = setOf("settings.gradle", "settings.gradle.kts")

    val repository = Repository(RepositoryId("tasktide"), "tasktide", null, null)

    fun of(root: Path, trusted: Boolean = true): BuildModelRequest {
        val files = object : WorkspaceFiles {
            override fun read(ref: ArtifactRef): ByteArray = Files.readAllBytes(absolutePath(ref))

            override fun absolutePath(ref: ArtifactRef): Path = root.resolve(ref.path.value)
        }
        return BuildModelRequest(repository, artifactsOf(root), files, trusted)
    }

    private fun artifactsOf(root: Path): List<Artifact> = Files.walk(root).use { paths ->
        paths.filter { Files.isRegularFile(it) && !isSkipped(root.relativize(it)) }
            .toList()
            .mapNotNull { artifactOf(root, it) }
            .sortedBy { it.ref.path.value }
    }

    private fun isSkipped(relative: Path) = relative.any { it.toString() in skippedDirectories }

    private fun artifactOf(root: Path, file: Path): Artifact? {
        val type = typeOf(file.fileName.toString()) ?: return null
        val path = RepoPath(root.relativize(file).toString().replace('\\', '/'))
        val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)))
        return Artifact(ArtifactRef(repository.id, path), type, ContentHash(hash), Files.size(file), 0, null, null)
    }

    private fun typeOf(name: String): ArtifactType? = when {
        name in settingsScripts -> ArtifactType("gradle.settings", ArtifactCategory.BUILD)
        name in buildScripts -> ArtifactType("gradle.build-script", ArtifactCategory.BUILD)
        name == "gradle.properties" -> ArtifactType("gradle.properties", ArtifactCategory.BUILD)
        name.endsWith(".versions.toml") -> ArtifactType("gradle.version-catalog", ArtifactCategory.BUILD)
        name.endsWith(".gradle.kts") -> ArtifactType("kotlin.script", ArtifactCategory.SOURCE)
        else -> sourceTypeOf(name)
    }

    private fun sourceTypeOf(name: String): ArtifactType? = when {
        name.endsWith(".kt") -> ArtifactType("kotlin.source", ArtifactCategory.SOURCE)
        name.endsWith(".java") -> ArtifactType("java.source", ArtifactCategory.SOURCE)
        else -> null
    }
}
