package io.github.krosstack.kscope.service.wiring

import io.github.krosstack.kscope.context.BlockMeasureKey
import io.github.krosstack.kscope.context.TokenCountKey
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.service.TemporaryTree
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal const val TOKEN_COUNT_FILE_NAME = "token-counts.bin"

internal fun blockKey(
    hash: String = "aa",
    range: TextRange = TextRange(1, 1, 3, 2),
    rendering: ContextRendering = ContextRendering.FILE,
    estimator: String = "o200k_base",
    layout: String = "layout",
) = BlockMeasureKey(ContentHash(hash), range, rendering, TokenizerId(estimator), layout)

internal fun derivedKey(
    hash: String = "aa",
    text: String = "text",
    rendering: ContextRendering = ContextRendering.SIGNATURE,
    estimator: String = "o200k_base",
    layout: String = "layout",
) = BlockMeasureKey(ContentHash(hash), null, rendering, TokenizerId(estimator), layout, text)

internal fun countKey(hash: String, digest: String = "layout", rendering: ContextRendering? = ContextRendering.FILE) =
    TokenCountKey(ContentHash(hash), TextRange(1, 1, 3, 2), rendering, TokenizerId("o200k_base"), digest)

internal fun packageKey(digest: String) =
    TokenCountKey(ContentHash(digest), null, null, TokenizerId("o200k_base"), digest)

internal fun <T> inTemporaryDirectory(block: (Path) -> T): T {
    val directory = Files.createTempDirectory("kscope-token-file")
    try {
        return block(directory)
    } finally {
        TemporaryTree.delete(directory)
    }
}

private const val FIRST_MEASURE = 111L
private const val SECOND_MEASURE = 222L

internal fun assertNotShared(first: BlockMeasureKey, second: BlockMeasureKey) = inTemporaryDirectory { directory ->
    val file = directory.resolve(TOKEN_COUNT_FILE_NAME)
    TokenCountFile(file).apply {
        rememberMeasure(first, FIRST_MEASURE)
        flush()
    }
    val cache = TokenCountFile(file)
    assertNull(cache.recallMeasure(second))

    cache.rememberMeasure(second, SECOND_MEASURE)
    cache.flush()

    val next = TokenCountFile(file)
    assertEquals(FIRST_MEASURE, next.recallMeasure(first))
    assertEquals(SECOND_MEASURE, next.recallMeasure(second))
}
