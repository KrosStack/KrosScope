package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.hashing.sha256
import io.github.krosstack.kscope.engine.hashing.toHex
import io.github.krosstack.kscope.model.ContentHash
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal class FileContent(val hash: ContentHash, val sizeBytes: Long, val lineCount: Int)

internal sealed interface ContentResult {
    class Read(val content: FileContent) : ContentResult

    class Unreadable(val reason: String) : ContentResult
}

/** Hashes a file and counts its lines in one pass. A final line without a terminator still counts. */
internal object ContentScanner {
    private const val BUFFER_BYTES = 64 * 1024
    private const val NEWLINE = '\n'.code.toByte()

    fun scan(file: Path): ContentResult = try {
        ContentResult.Read(read(file))
    } catch (failure: IOException) {
        ContentResult.Unreadable(failure.message ?: failure.javaClass.simpleName)
    }

    private fun read(file: Path): FileContent {
        val digest = sha256()
        val buffer = ByteArray(BUFFER_BYTES)
        var size = 0L
        var newlines = 0
        var lastByte = NEWLINE
        Files.newInputStream(file).use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                newlines += buffer.countNewlines(count)
                size += count
                if (count > 0) lastByte = buffer[count - 1]
            }
        }
        val unterminatedTail = if (size > 0 && lastByte != NEWLINE) 1 else 0
        return FileContent(ContentHash(digest.digest().toHex()), size, newlines + unterminatedTail)
    }

    private fun ByteArray.countNewlines(length: Int): Int = (0 until length).count { this[it] == NEWLINE }
}
