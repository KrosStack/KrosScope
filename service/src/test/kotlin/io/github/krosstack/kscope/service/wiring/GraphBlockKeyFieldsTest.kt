package io.github.krosstack.kscope.service.wiring

import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextRendering
import kotlin.test.Test

class GraphBlockKeyFieldsTest {
    @Test
    fun `a block with the digest of a text and a block without one do not share a measure`() {
        assertNotShared(blockKey(), blockKey().copy(textDigest = "text"))
        assertNotShared(derivedKey().copy(range = RANGE), derivedKey().copy(range = RANGE, textDigest = null))
    }

    @Test
    fun `a text digest that reads like the tag of a block without one does not share a measure`() {
        assertNotShared(blockKey(), blockKey().copy(textDigest = "BLOCK"))
        assertNotShared(derivedKey(text = "BLOCK"), blockKey(rendering = ContextRendering.SIGNATURE))
    }

    @Test
    fun `blocks that differ only in the digest of their text do not share a measure`() =
        assertNotShared(derivedKey(), derivedKey(text = "other"))

    @Test
    fun `blocks with a text digest and a range do not share a measure with the same blocks without a range`() =
        assertNotShared(derivedKey(), derivedKey().copy(range = RANGE))

    @Test
    fun `blocks with a text digest that differ in one coordinate of the range do not share a measure`() {
        val keys = listOf(derivedKey().copy(range = RANGE), derivedKey().copy(range = RANGE.copy(endColumn = 8)))

        assertNotShared(keys.first(), keys.last())
    }

    @Test
    fun `blocks with a text digest of different content do not share a measure`() =
        assertNotShared(derivedKey(), derivedKey(hash = "ab"))

    @Test
    fun `blocks with a text digest of different renderings do not share a measure`() = ContextRendering.entries
        .filter { it != ContextRendering.SIGNATURE }
        .forEach { assertNotShared(derivedKey(rendering = ContextRendering.SIGNATURE), derivedKey(rendering = it)) }

    @Test
    fun `blocks with a text digest counted by different estimators do not share a measure`() =
        assertNotShared(derivedKey(), derivedKey(estimator = "cl100k_base"))

    @Test
    fun `blocks with a text digest and a different layout do not share a measure`() =
        assertNotShared(derivedKey(), derivedKey(layout = "other layout"))

    private companion object {
        val RANGE = TextRange(2, 3, 5, 7)
    }
}
