package io.github.krosstack.kscope.service.wiring

import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.TokenCount
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TokenCountFileDerivedMeasureTest {
    @Test
    fun `measures of blocks from the graph written by one run are recalled by the next`() =
        inTemporaryDirectory { directory ->
            val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
            val ranged = derivedKey("cc").copy(range = RANGE)
            TokenCountFile(file).apply {
                rememberMeasure(derivedKey("aa"), 120L)
                rememberMeasure(derivedKey("aa", rendering = ContextRendering.OUTLINE), 7L)
                rememberMeasure(ranged, 33L)
                flush()
            }

            val next = TokenCountFile(file)

            assertEquals(120L, next.recallMeasure(derivedKey("aa")))
            assertEquals(7L, next.recallMeasure(derivedKey("aa", rendering = ContextRendering.OUTLINE)))
            assertEquals(33L, next.recallMeasure(ranged))
            assertNull(next.recallMeasure(derivedKey("bb")))
            assertNull(next.recallMeasure(derivedKey("cc")))
        }

    @Test
    fun `a measure of a block from the graph sits beside the other entries of the file`() =
        inTemporaryDirectory { directory ->
            val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
            TokenCountFile(file).apply {
                rememberMeasure(derivedKey("aa"), 1L)
                rememberMeasure(blockKey("aa"), 2L)
                remember(countKey("aa"), TokenCount(3))
                remember(packageKey("pp"), TokenCount(4))
                flush()
            }

            val next = TokenCountFile(file)

            assertEquals(1L, next.recallMeasure(derivedKey("aa")))
            assertEquals(2L, next.recallMeasure(blockKey("aa")))
            assertEquals(TokenCount(3), next.recall(countKey("aa")))
            assertEquals(TokenCount(4), next.recall(packageKey("pp")))
            assertEquals(4, TokenCountFileBytes.read(file).size)
        }

    @Test
    fun `a file written before blocks from the graph were kept is still read and keeps its entries`() =
        inTemporaryDirectory { directory ->
            val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
            TokenCountFileBytes.write(file, mapOf(COUNT to 7, FILE_BLOCK to 120, PACKAGE to 9))

            val cache = TokenCountFile(file)
            assertEquals(120L, cache.recallMeasure(blockKey("aa", layout = "d")))
            assertEquals(TokenCount(7), cache.recall(countKey("aa", digest = "d")))
            assertEquals(TokenCount(9), cache.recall(packageKey("bb")))
            assertNull(cache.recallMeasure(derivedKey("aa", layout = "d")))
            cache.rememberMeasure(derivedKey("aa", layout = "d"), 5L)
            cache.flush()

            val next = TokenCountFile(file)
            assertEquals(5L, next.recallMeasure(derivedKey("aa", layout = "d")))
            assertEquals(120L, next.recallMeasure(blockKey("aa", layout = "d")))
            assertEquals(TokenCount(7), next.recall(countKey("aa", digest = "d")))
            assertEquals(4, TokenCountFileBytes.read(file).size)
        }

    @Test
    fun `a measure of a block from the graph offered again with the same value makes no write`() =
        inTemporaryDirectory { directory ->
            val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
            TokenCountFile(file).apply {
                rememberMeasure(derivedKey(), 120L)
                flush()
            }
            Files.setLastModifiedTime(file, UNTOUCHED)

            TokenCountFile(file).apply {
                recallMeasure(derivedKey())
                rememberMeasure(derivedKey(), 120L)
                flush()
            }

            assertEquals(UNTOUCHED, Files.getLastModifiedTime(file))
        }

    @Test
    fun `a block from the graph with the separator of the file in its key is not remembered`() =
        inTemporaryDirectory { directory ->
            val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
            val cache = TokenCountFile(file)
            val keys = listOf(
                derivedKey(text = "x|y"),
                derivedKey(hash = "a|b"),
                derivedKey(estimator = "o200k|base"),
                derivedKey(layout = "x|y"),
            )

            keys.forEach { cache.rememberMeasure(it, 5L) }
            cache.flush()

            keys.forEach { assertNull(cache.recallMeasure(it)) }
            assertFalse(Files.exists(file))
        }

    @Test
    fun `a prune drops the measures of blocks from the graph with their content and keeps the others`() =
        inTemporaryDirectory { directory ->
            val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
            val cache = TokenCountFile(file)
            cache.rememberMeasure(derivedKey("live"), 1L)
            cache.rememberMeasure(derivedKey("gone"), 2L)
            cache.rememberMeasure(derivedKey("gone").copy(range = RANGE), 3L)
            cache.rememberMeasure(blockKey("live"), 4L)
            cache.rememberMeasure(blockKey("gone"), 5L)
            cache.remember(countKey("live"), TokenCount(6))

            cache.prune(setOf(ContentHash("live")))

            val next = TokenCountFile(file)
            assertEquals(1L, next.recallMeasure(derivedKey("live")))
            assertNull(next.recallMeasure(derivedKey("gone")))
            assertNull(next.recallMeasure(derivedKey("gone").copy(range = RANGE)))
            assertEquals(4L, next.recallMeasure(blockKey("live")))
            assertNull(next.recallMeasure(blockKey("gone")))
            assertEquals(TokenCount(6), next.recall(countKey("live")))
            assertEquals(3, TokenCountFileBytes.read(file).size)
        }

    @Test
    fun `a prune drops every measure of a block from the graph when no content is live`() =
        inTemporaryDirectory { directory ->
            val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
            val cache = TokenCountFile(file)
            cache.rememberMeasure(derivedKey("aa"), 1L)
            cache.rememberMeasure(derivedKey("bb", rendering = ContextRendering.GENERATED), 2L)

            cache.prune(emptySet())

            assertEquals(emptyMap(), TokenCountFileBytes.read(file))
        }

    @Test
    fun `discarding forgets the measures of blocks from the graph and removes the file`() =
        inTemporaryDirectory { directory ->
            val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
            val cache = TokenCountFile(file)
            cache.rememberMeasure(derivedKey(), 120L)
            cache.flush()

            cache.discard()

            assertFalse(Files.exists(file))
            assertNull(cache.recallMeasure(derivedKey()))
        }

    @Test
    fun `reloading forgets graph block measures that were not written and reads the ones another run wrote`() =
        inTemporaryDirectory { directory ->
            val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
            val cache = TokenCountFile(file)
            cache.rememberMeasure(derivedKey("aa"), 1L)
            cache.flush()
            cache.rememberMeasure(derivedKey("bb"), 2L)
            TokenCountFile(file).apply {
                rememberMeasure(derivedKey("cc"), 3L)
                flush()
            }

            cache.reload()

            assertEquals(1L, cache.recallMeasure(derivedKey("aa")))
            assertNull(cache.recallMeasure(derivedKey("bb")))
            assertEquals(3L, cache.recallMeasure(derivedKey("cc")))
        }

    private companion object {
        const val COUNT = "aa|1:1-3:2|FILE|o200k_base|d"
        const val FILE_BLOCK = "aa|1:1-3:2|FILE|o200k_base|d|BLOCK"
        const val PACKAGE = "bb|-|PACKAGE|o200k_base|bb"
        val RANGE = TextRange(2, 3, 5, 7)
        val UNTOUCHED: FileTime = FileTime.fromMillis(1_000_000_000_000L)
    }
}
