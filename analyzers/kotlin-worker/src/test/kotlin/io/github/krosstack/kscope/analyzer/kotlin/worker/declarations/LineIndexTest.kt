package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class LineIndexTest {
    private val index = LineIndex("ab\ncde\n\nf")

    @Test
    fun `positions are one based`() {
        assertEquals(TextRange(1, 1, 1, 2), index.range(0, 1))
    }

    @Test
    fun `a range may span lines and ends exclusive`() {
        assertEquals(TextRange(1, 2, 2, 3), index.range(1, 5))
    }

    @Test
    fun `the offset after a line break is the start of the next line`() {
        assertEquals(TextRange(2, 1, 3, 1), index.range(3, 7))
    }

    @Test
    fun `the end of the text is addressable`() {
        assertEquals(TextRange(4, 1, 4, 2), index.range(8, 9))
    }
}
