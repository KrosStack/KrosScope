package io.github.krosstack.kscope.service.wiring

import io.github.krosstack.kscope.context.TokenCountKey
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenCountFileTest {
    private fun key(hash: String, digest: String = "d", rendering: ContextRendering? = ContextRendering.FILE) =
        TokenCountKey(ContentHash(hash), TextRange(1, 1, 3, 2), rendering, TokenizerId("o200k_base"), digest)

    private fun <T> inDirectory(block: (Path) -> T): T {
        val directory = Files.createTempDirectory("kscope-token-file")
        try {
            return block(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `counts written by one run are recalled by the next`() = inDirectory { directory ->
        val file = directory.resolve("token-counts.bin")
        TokenCountFile(file).apply {
            remember(key("aa"), TokenCount(7))
            remember(key("bb", rendering = null), TokenCount(9))
            flush()
        }

        val next = TokenCountFile(file)

        assertEquals(TokenCount(7), next.recall(key("aa")))
        assertEquals(TokenCount(9), next.recall(key("bb", rendering = null)))
        assertNull(next.recall(key("aa", digest = "other")))
    }

    @Test
    fun `nothing is written when nothing was added`() = inDirectory { directory ->
        val file = directory.resolve("token-counts.bin")

        TokenCountFile(file).flush()

        assertFalse(Files.exists(file))
    }

    @Test
    fun `a file that is not a cache is ignored and replaced by the next flush`() = inDirectory { directory ->
        val file = directory.resolve("token-counts.bin")
        Files.writeString(file, "this is not a token count file")

        val cache = TokenCountFile(file)
        assertNull(cache.recall(key("aa")))
        cache.remember(key("aa"), TokenCount(3))
        cache.flush()

        assertEquals(TokenCount(3), TokenCountFile(file).recall(key("aa")))
    }

    @Test
    fun `a file cut short is ignored`() = inDirectory { directory ->
        val file = directory.resolve("token-counts.bin")
        TokenCountFile(file).apply {
            remember(key("aa"), TokenCount(7))
            flush()
        }
        val bytes = Files.readAllBytes(file)
        Files.write(file, bytes.copyOf(bytes.size - 3))

        assertNull(TokenCountFile(file).recall(key("aa")))
    }

    @Test
    fun `a file of another format version is ignored`() = inDirectory { directory ->
        val file = directory.resolve("token-counts.bin")
        TokenCountFile(file).apply {
            remember(key("aa"), TokenCount(7))
            flush()
        }
        val bytes = Files.readAllBytes(file)
        bytes[MAGIC_BYTES + VERSION_LAST_BYTE] = (bytes[MAGIC_BYTES + VERSION_LAST_BYTE] + 1).toByte()
        Files.write(file, bytes)

        assertNull(TokenCountFile(file).recall(key("aa")))
    }

    @Test
    fun `a file with a negative entry count is ignored and replaced by the next flush`() = inDirectory { directory ->
        val file = directory.resolve("token-counts.bin")
        TokenCountFile(file).apply {
            remember(key("aa"), TokenCount(7))
            flush()
        }
        val bytes = Files.readAllBytes(file)
        ByteBuffer.wrap(bytes).putInt(ENTRY_COUNT_OFFSET, -1)
        Files.write(file, bytes)

        val cache = TokenCountFile(file)
        assertNull(cache.recall(key("aa")))
        cache.remember(key("bb"), TokenCount(3))
        cache.flush()

        assertEquals(TokenCount(3), TokenCountFile(file).recall(key("bb")))
    }

    @Test
    fun `a prune keeps live content and recent packages and drops the rest`() = inDirectory { directory ->
        val file = directory.resolve("token-counts.bin")
        val cache = TokenCountFile(file)
        cache.remember(key("live"), TokenCount(1))
        cache.remember(key("gone"), TokenCount(2))
        cache.remember(key("package", rendering = null), TokenCount(3))

        cache.prune(setOf(ContentHash("live")))

        val next = TokenCountFile(file)
        assertEquals(TokenCount(1), next.recall(key("live")))
        assertNull(next.recall(key("gone")))
        assertEquals(TokenCount(3), next.recall(key("package", rendering = null)))
    }

    @Test
    fun `discarding removes the file`() = inDirectory { directory ->
        val file = directory.resolve("token-counts.bin")
        val cache = TokenCountFile(file)
        cache.remember(key("aa"), TokenCount(7))
        cache.flush()
        assertTrue(Files.exists(file))

        cache.discard()

        assertFalse(Files.exists(file))
        assertNull(cache.recall(key("aa")))
    }

    @Test
    fun `discarding leaves a file that cannot be deleted and does not fail`() = inDirectory { directory ->
        val file = directory.resolve("token-counts.bin")
        Files.createDirectory(file)
        Files.writeString(file.resolve("in-the-way"), "not a cache")
        val cache = TokenCountFile(file)

        cache.discard()

        assertTrue(Files.exists(file))
        assertNull(cache.recall(key("aa")))
    }

    private companion object {
        const val MAGIC_BYTES = 8
        const val VERSION_LAST_BYTE = 3
        const val ENTRY_COUNT_OFFSET = 12
    }
}
