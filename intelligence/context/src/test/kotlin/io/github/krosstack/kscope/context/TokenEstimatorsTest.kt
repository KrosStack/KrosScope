package io.github.krosstack.kscope.context

import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class TokenEstimatorsTest {
    @Test
    fun `declared BPE estimators count known ordinary token boundaries`() {
        for (id in listOf("o200k_base", "cl100k_base")) {
            val estimator = TokenEstimators.create(TokenizerId(id))

            assertEquals(TokenizerId(id), estimator.id)
            assertEquals(TokenCount(2), estimator.count("hello world"))
        }
    }

    @Test
    fun `character heuristic exposes its explicit identifier and count convention`() {
        val estimator = TokenEstimators.create(TokenizerId("chars"))

        assertEquals(TokenizerId("chars_4"), estimator.id)
        assertEquals(TokenCount(2), estimator.count("12345678"))
    }

    @Test
    fun `BPE truncation preserves Unicode code points and retokenizes within budget`() {
        val estimator = TokenEstimators.create()
        val text = "α🙂漢字".repeat(6)
        val truncated = estimator.truncate(text, TokenCount(8))

        assertTrue(truncated.isNotEmpty())
        assertTrue(text.startsWith(truncated))
        assertTrue(estimator.count(truncated).value <= 8)
        assertTrue(truncated.codePoints().noneMatch { it in 0xD800..0xDFFF })
    }
}
