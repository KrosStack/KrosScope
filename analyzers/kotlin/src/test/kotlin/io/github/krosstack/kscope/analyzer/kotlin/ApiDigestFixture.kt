package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.nio.file.Files
import java.nio.file.Path

internal class ApiDigestFixture(private val root: Path) : WorkspaceFiles {
    fun source(name: String, text: String): Artifact {
        Files.writeString(root.resolve(name), text)
        return Artifact(
            ArtifactRef(RepositoryId("sample"), RepoPath(name)),
            ArtifactType("kotlin.source", ArtifactCategory.SOURCE),
            ContentHash("content"),
            text.toByteArray(Charsets.UTF_8).size.toLong(),
            text.lines().size,
            null,
            null,
        )
    }

    override fun read(ref: ArtifactRef): ByteArray = Files.readAllBytes(absolutePath(ref))

    override fun absolutePath(ref: ArtifactRef): Path = root.resolve(ref.path.value)
}
