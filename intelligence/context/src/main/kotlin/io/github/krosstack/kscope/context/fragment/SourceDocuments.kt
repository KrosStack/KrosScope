package io.github.krosstack.kscope.context.fragment

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.context.ContextOmissionReason
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.HexFormat

internal sealed interface DocumentRead {
    data class Content(val document: SourceDocument) : DocumentRead
    data class Failed(val reason: ContextOmissionReason) : DocumentRead
}

internal class SourceDocuments(private val files: WorkspaceFiles) {
    private val cache = mutableMapOf<Pair<ArtifactRef, ContentHash>, DocumentRead>()

    fun read(artifact: Artifact): DocumentRead = cache.getOrPut(
        artifact.ref to artifact.contentHash,
    ) { readContent(artifact) }

    private fun readContent(artifact: Artifact): DocumentRead {
        if (artifact.sizeBytes > MAX_TEXT_BYTES) return DocumentRead.Failed(ContextOmissionReason.OVERSIZED)
        return try {
            val bytes = files.read(artifact.ref)
            val hash = ContentHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))
            when {
                bytes.size > MAX_TEXT_BYTES -> DocumentRead.Failed(ContextOmissionReason.OVERSIZED)
                hash != artifact.contentHash -> DocumentRead.Failed(ContextOmissionReason.STALE_INDEX)
                else -> decode(bytes)
            }
        } catch (_: IOException) {
            DocumentRead.Failed(ContextOmissionReason.UNREADABLE)
        }
    }

    private fun decode(bytes: ByteArray): DocumentRead = try {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(ByteBuffer.wrap(bytes)).toString()
        if ('\u0000' in text) {
            DocumentRead.Failed(ContextOmissionReason.BINARY)
        } else {
            DocumentRead.Content(SourceDocument(text))
        }
    } catch (_: CharacterCodingException) {
        DocumentRead.Failed(ContextOmissionReason.BINARY)
    }

    private companion object {
        const val MAX_TEXT_BYTES = 2L * 1024 * 1024
    }
}
