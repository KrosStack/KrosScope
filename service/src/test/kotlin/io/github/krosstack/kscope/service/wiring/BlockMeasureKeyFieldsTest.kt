package io.github.krosstack.kscope.service.wiring

import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextRendering
import kotlin.test.Test

class BlockMeasureKeyFieldsTest {
    @Test
    fun `blocks of different content do not share a measure`() = assertNotShared(blockKey(), blockKey(hash = "ab"))

    @Test
    fun `blocks that differ in one coordinate of the range do not share a measure`() {
        val range = TextRange(2, 3, 5, 7)
        val variants = listOf(
            range.copy(startLine = range.startLine + 1),
            range.copy(startColumn = range.startColumn + 1),
            range.copy(endLine = range.endLine + 1),
            range.copy(endColumn = range.endColumn + 1),
            TextRange(range.startColumn, range.startLine, range.endLine, range.endColumn),
            TextRange(range.startLine, range.startColumn, range.endColumn, range.endLine),
        )

        variants.forEach { assertNotShared(blockKey(range = range), blockKey(range = it)) }
    }

    @Test
    fun `coordinates that run together as digits do not share a measure`() = assertNotShared(
        blockKey(range = TextRange(1, 11, 1, 1)),
        blockKey(range = TextRange(11, 1, 1, 1)),
    )

    @Test
    fun `blocks of different renderings do not share a measure`() = ContextRendering.entries
        .filter { it != ContextRendering.FILE }
        .forEach { assertNotShared(blockKey(rendering = ContextRendering.FILE), blockKey(rendering = it)) }

    @Test
    fun `blocks counted by different estimators do not share a measure`() =
        assertNotShared(blockKey(), blockKey(estimator = "cl100k_base"))

    @Test
    fun `blocks with a different layout do not share a measure`() =
        assertNotShared(blockKey(), blockKey(layout = "other layout"))
}
