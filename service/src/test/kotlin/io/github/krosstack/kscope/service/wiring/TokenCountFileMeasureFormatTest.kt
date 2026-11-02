package io.github.krosstack.kscope.service.wiring

import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.TokenCount
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenCountFileMeasureFormatTest {
    @Test
    fun `a file written before block measures existed is still read`() = inTemporaryDirectory { directory ->
        val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
        TokenCountFileBytes.write(file, mapOf(BEFORE_KEY to 7, "bb|-|PACKAGE|o200k_base|bb" to 9))

        val cache = TokenCountFile(file)

        assertEquals(TokenCount(7), cache.recall(countKey("aa", digest = "d")))
        assertEquals(TokenCount(9), cache.recall(packageKey("bb")))
        assertNull(cache.recallMeasure(blockKey("aa", layout = "d")))
    }

    @Test
    fun `measures added to a file written before are written beside its counts`() = inTemporaryDirectory { directory ->
        val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
        TokenCountFileBytes.write(file, mapOf(BEFORE_KEY to 7))
        TokenCountFile(file).apply {
            rememberMeasure(blockKey("cc"), 120L)
            flush()
        }

        val next = TokenCountFile(file)

        assertEquals(TokenCount(7), next.recall(countKey("aa", digest = "d")))
        assertEquals(120L, next.recallMeasure(blockKey("cc")))
        assertEquals(2, TokenCountFileBytes.read(file).size)
    }

    @Test
    fun `a file with measures of another version is ignored and replaced`() = inTemporaryDirectory { directory ->
        val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
        TokenCountFile(file).apply {
            rememberMeasure(blockKey("aa"), 120L)
            flush()
        }
        TokenCountFileBytes.write(file, TokenCountFileBytes.read(file), version = 2)

        val cache = TokenCountFile(file)
        assertNull(cache.recallMeasure(blockKey("aa")))
        cache.rememberMeasure(blockKey("bb"), 5L)
        cache.flush()

        val next = TokenCountFile(file)
        assertNull(next.recallMeasure(blockKey("aa")))
        assertEquals(5L, next.recallMeasure(blockKey("bb")))
    }

    @Test
    fun `a file with measures that was cut short is ignored and replaced`() = inTemporaryDirectory { directory ->
        val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
        TokenCountFile(file).apply {
            rememberMeasure(blockKey("aa"), 120L)
            rememberMeasure(blockKey("bb"), 5L)
            flush()
        }
        val bytes = Files.readAllBytes(file)
        Files.write(file, bytes.copyOf(bytes.size - 3))

        val cache = TokenCountFile(file)
        assertNull(cache.recallMeasure(blockKey("aa")))
        assertNull(cache.recallMeasure(blockKey("bb")))
        cache.rememberMeasure(blockKey("cc"), 8L)
        cache.flush()

        assertEquals(8L, TokenCountFile(file).recallMeasure(blockKey("cc")))
    }

    @Test
    fun `a file with a negative measure is ignored as damaged`() = inTemporaryDirectory { directory ->
        val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
        TokenCountFile(file).apply {
            rememberMeasure(blockKey("aa"), 120L)
            flush()
        }
        val key = TokenCountFileBytes.read(file).keys.single()
        TokenCountFileBytes.write(file, mapOf(key to -1))

        assertNull(TokenCountFile(file).recallMeasure(blockKey("aa")))
    }

    @Test
    fun `a prune drops the measures of content that is gone and keeps the others`() =
        inTemporaryDirectory { directory ->
            val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
            val cache = TokenCountFile(file)
            cache.rememberMeasure(blockKey("live"), 1L)
            cache.rememberMeasure(blockKey("gone"), 2L)
            cache.remember(countKey("live"), TokenCount(3))
            cache.remember(countKey("gone"), TokenCount(4))

            cache.prune(setOf(ContentHash("live")))

            val next = TokenCountFile(file)
            assertEquals(1L, next.recallMeasure(blockKey("live")))
            assertNull(next.recallMeasure(blockKey("gone")))
            assertEquals(TokenCount(3), next.recall(countKey("live")))
            assertNull(next.recall(countKey("gone")))
        }

    @Test
    fun `a prune drops every measure when no content is live`() = inTemporaryDirectory { directory ->
        val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
        val cache = TokenCountFile(file)
        cache.rememberMeasure(blockKey("aa"), 1L)
        cache.rememberMeasure(blockKey("bb", rendering = ContextRendering.ENTRY), 2L)

        cache.prune(emptySet())

        assertEquals(emptyMap(), TokenCountFileBytes.read(file))
    }

    @Test
    fun `measures are not counted among the packages that a prune keeps`() = inTemporaryDirectory { directory ->
        val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
        val cache = TokenCountFile(file)
        cache.rememberMeasure(blockKey("live"), 1L)
        repeat(PACKAGES_OVER_THE_LIMIT) { cache.remember(packageKey("p$it"), TokenCount(it)) }

        cache.prune(setOf(ContentHash("live")))

        assertEquals(1L, TokenCountFile(file).recallMeasure(blockKey("live")))
    }

    private companion object {
        const val BEFORE_KEY = "aa|1:1-3:2|FILE|o200k_base|d"
        const val PACKAGES_OVER_THE_LIMIT = 600
    }
}
