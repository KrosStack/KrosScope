package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest
import java.util.HexFormat

/** Reads the source an artifact was indexed from; an artifact without chunks differs from one that cannot be read. */
internal object SemanticSources {
    const val MAX_SOURCE_BYTES = 2 * 1024 * 1024

    sealed interface Read {
        data class Content(val text: String) : Read
        data object Excluded : Read
        data object Stale : Read
        data object Unreadable : Read
    }

    fun read(artifact: Artifact, files: WorkspaceFiles): Read {
        if (artifact.generated != null || artifact.sizeBytes > MAX_SOURCE_BYTES) return Read.Excluded
        return try {
            val bytes = files.read(artifact.ref)
            val digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
            if (bytes.size > MAX_SOURCE_BYTES || digest != artifact.contentHash.value) {
                Read.Stale
            } else {
                val text = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
                if ('\u0000' in text) Read.Excluded else Read.Content(text)
            }
        } catch (_: CharacterCodingException) {
            Read.Excluded
        } catch (_: IOException) {
            Read.Unreadable
        }
    }
}
