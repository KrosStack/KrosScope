package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.hashing.sha256
import io.github.krosstack.kscope.engine.hashing.toHex
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.spi.WorkspaceFiles
import io.github.krosstack.kscope.spi.search.SearchContent
import java.io.IOException

/**
 * The text of hand-written files that a producer may also read: sources, build files, resources, schemas,
 * configuration and documentation, below the size limit. Every other artifact is searched by its path.
 */
internal class WorkspaceSearchContent(private val files: WorkspaceFiles, private val maxArtifactBytes: Long) :
    SearchContent {
    override fun isIndexed(artifact: Artifact): Boolean = artifact.generated == null &&
        artifact.type.category in INDEXED_CATEGORIES &&
        artifact.sizeBytes <= maxArtifactBytes

    override fun textOf(artifact: Artifact): String? {
        val bytes = try {
            files.read(artifact.ref)
        } catch (ignored: IOException) {
            return null
        }
        return if (sha256().digest(bytes).toHex() == artifact.contentHash.value) {
            String(bytes, Charsets.UTF_8).takeUnless { NUL in it }
        } else {
            null
        }
    }

    private companion object {
        const val NUL = '\u0000'

        val INDEXED_CATEGORIES = setOf(
            ArtifactCategory.SOURCE,
            ArtifactCategory.BUILD,
            ArtifactCategory.RESOURCE,
            ArtifactCategory.SCHEMA,
            ArtifactCategory.CONFIG,
            ArtifactCategory.DOCUMENTATION,
        )
    }
}
