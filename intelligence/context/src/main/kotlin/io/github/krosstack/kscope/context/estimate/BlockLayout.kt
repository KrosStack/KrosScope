package io.github.krosstack.kscope.context.estimate

import io.github.krosstack.kscope.context.token.BpeBlockBoundary
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.HexFormat

/**
 * What a kept block measure assumes about the text of a block beyond the file it comes from. [VERSION] is raised
 * whenever the markdown of a fragment block or the slicing of a rendering changes, so that no measure kept under the
 * old layout is recalled; a golden test pins both.
 */
internal object BlockLayout {
    const val VERSION = 1
    private const val CHUNK_CHARACTERS = 4096

    /** Hashes UTF-16 code units, because a path that is not valid Unicode must not collapse into another path. */
    fun digest(printedPath: String, evidenceNote: String?): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(VERSION)
            out.writeInt(printedPath.length)
            out.writeChars(printedPath)
            out.writeBoolean(evidenceNote != null)
            evidenceNote?.let {
                out.writeInt(it.length)
                out.writeChars(it)
            }
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()))
    }

    /** Hashes UTF-16 code units for the same reason as [digest], in chunks, because a text can be long. */
    fun textDigest(text: String): String {
        val sha = MessageDigest.getInstance("SHA-256")
        val bytes = ByteBuffer.allocate(CHUNK_CHARACTERS * Char.SIZE_BYTES)
        for (start in text.indices step CHUNK_CHARACTERS) {
            val end = minOf(text.length, start + CHUNK_CHARACTERS)
            bytes.clear()
            bytes.asCharBuffer().put(text, start, end)
            sha.update(bytes.array(), 0, (end - start) * Char.SIZE_BYTES)
        }
        return HexFormat.of().formatHex(sha.digest())
    }

    /**
     * A fragment block opens with a fence and closes with a blank line, and the printed path, the evidence note and
     * the text each lie between ASCII characters, so no surrogate pair can span two of them. The block is therefore
     * standalone exactly when those three parts are valid Unicode, which spares building the block to find out.
     */
    fun standalone(printedPath: String, evidenceNote: String?, text: String): Boolean =
        BpeBlockBoundary.valid(printedPath) && BpeBlockBoundary.valid(evidenceNote.orEmpty()) &&
            BpeBlockBoundary.valid(text)
}
