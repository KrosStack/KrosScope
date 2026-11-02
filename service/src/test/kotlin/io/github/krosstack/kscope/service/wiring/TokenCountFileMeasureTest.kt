package io.github.krosstack.kscope.service.wiring

import io.github.krosstack.kscope.model.context.TokenCount
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenCountFileMeasureTest {
    @Test
    fun `measures written by one run are recalled by the next`() = inTemporaryDirectory { directory ->
        val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
        TokenCountFile(file).apply {
            rememberMeasure(blockKey("aa"), 120L)
            rememberMeasure(blockKey("bb"), 7L)
            flush()
        }

        val next = TokenCountFile(file)

        assertEquals(120L, next.recallMeasure(blockKey("aa")))
        assertEquals(7L, next.recallMeasure(blockKey("bb")))
        assertNull(next.recallMeasure(blockKey("cc")))
    }

    @Test
    fun `a measure and a count of the same content are different entries`() = inTemporaryDirectory { directory ->
        val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
        TokenCountFile(file).apply {
            rememberMeasure(blockKey("aa"), 120L)
            remember(countKey("bb"), TokenCount(9))
            flush()
        }

        val next = TokenCountFile(file)

        assertNull(next.recall(countKey("aa")))
        assertNull(next.recallMeasure(blockKey("bb")))
        assertEquals(120L, next.recallMeasure(blockKey("aa")))
        assertEquals(TokenCount(9), next.recall(countKey("bb")))
    }

    @Test
    fun `a measure that is offered again with the same value makes no flush write`() =
        inTemporaryDirectory { directory ->
            val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
            TokenCountFile(file).apply {
                rememberMeasure(blockKey(), 120L)
                flush()
            }
            Files.setLastModifiedTime(file, UNTOUCHED)

            TokenCountFile(file).apply {
                recallMeasure(blockKey())
                rememberMeasure(blockKey(), 120L)
                flush()
            }

            assertEquals(UNTOUCHED, Files.getLastModifiedTime(file))
        }

    @Test
    fun `the smallest and the largest measure the format holds are kept`() = inTemporaryDirectory { directory ->
        val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
        TokenCountFile(file).apply {
            rememberMeasure(blockKey("aa"), 0L)
            rememberMeasure(blockKey("bb"), Int.MAX_VALUE.toLong())
            flush()
        }

        val next = TokenCountFile(file)

        assertEquals(0L, next.recallMeasure(blockKey("aa")))
        assertEquals(Int.MAX_VALUE.toLong(), next.recallMeasure(blockKey("bb")))
    }

    @Test
    fun `a measure that the format cannot hold is not remembered and leaves the file alone`() =
        inTemporaryDirectory { directory ->
            val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
            val cache = TokenCountFile(file)

            cache.rememberMeasure(blockKey("aa"), -1L)
            cache.rememberMeasure(blockKey("bb"), Int.MAX_VALUE.toLong() + 1)
            cache.rememberMeasure(blockKey("cc"), Long.MAX_VALUE)
            cache.flush()

            assertNull(cache.recallMeasure(blockKey("aa")))
            assertNull(cache.recallMeasure(blockKey("bb")))
            assertNull(cache.recallMeasure(blockKey("cc")))
            assertFalse(Files.exists(file))
        }

    @Test
    fun `a block with the separator of the file in its key is not remembered`() = inTemporaryDirectory { directory ->
        val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
        val cache = TokenCountFile(file)
        val keys = listOf(blockKey(hash = "a|b"), blockKey(estimator = "o200k|base"), blockKey(layout = "x|y"))

        keys.forEach { cache.rememberMeasure(it, 5L) }
        cache.flush()

        keys.forEach { assertNull(cache.recallMeasure(it)) }
        assertFalse(Files.exists(file))
    }

    @Test
    fun `discarding forgets the measures and removes the file`() = inTemporaryDirectory { directory ->
        val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
        val cache = TokenCountFile(file)
        cache.rememberMeasure(blockKey(), 120L)
        cache.flush()
        assertTrue(Files.exists(file))

        cache.discard()

        assertFalse(Files.exists(file))
        assertNull(cache.recallMeasure(blockKey()))
    }

    @Test
    fun `reloading drops measures that were not written and reads the ones another run wrote`() =
        inTemporaryDirectory { directory ->
            val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
            val cache = TokenCountFile(file)
            cache.rememberMeasure(blockKey("aa"), 1L)
            cache.flush()
            cache.rememberMeasure(blockKey("bb"), 2L)
            TokenCountFile(file).apply {
                rememberMeasure(blockKey("cc"), 3L)
                flush()
            }

            cache.reload()

            assertEquals(1L, cache.recallMeasure(blockKey("aa")))
            assertNull(cache.recallMeasure(blockKey("bb")))
            assertEquals(3L, cache.recallMeasure(blockKey("cc")))
        }

    private companion object {
        val UNTOUCHED: FileTime = FileTime.fromMillis(1_000_000_000_000L)
    }
}
