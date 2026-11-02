package io.github.krosstack.kscope.context.token

import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.model.context.TokenizerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.measureTimedValue

internal class MarkdownBoundaryPropertyTest {
    @Test
    fun `generated heavy packages beyond two hundred fifty six blocks retain exact reused counts`() {
        val blocks = (0 until 600).map { index ->
            "``` generated/Surface$index [generated]\nclass Surface$index\nGenerated from schema/Base\n```\n\n"
        }
        for (id in listOf("o200k_base", "cl100k_base")) {
            val estimator = TokenEstimators.create(TokenizerId(id))
            val canonical = measureTimedValue { estimator.count(blocks.joinToString("")) }
            val first = measureTimedValue { estimator.count(blocks) }
            val repeated = measureTimedValue { estimator.count(blocks) }

            assertEquals(canonical.value, first.value)
            assertEquals(canonical.value, repeated.value)
            println(
                "generated-blocks encoding=$id count=600 canonicalUs=${canonical.duration.inWholeMicroseconds} " +
                    "coldUs=${first.duration.inWholeMicroseconds} hotUs=${repeated.duration.inWholeMicroseconds}",
            )
        }
    }

    @Test
    fun `double newline before non whitespace non slash is an exact ordinary BPE boundary`() {
        val suffixes = listOf(
            "", "a", "A", "can't", "123456", "   ", "\t", "\r", "\n", "\r\n", "\u2028", "\u2029",
            "\u00a0", "\u0085", "/", "//", "\\", "```", " ```\n", "α🙂漢字", "👩‍💻", "🏳️‍🌈", "é",
            "İıΣςσ", "العربية", "שָׁלוֹם", "क्‌ष", "<|endoftext|>", "<|fim_prefix|>",
        )
        val next = listOf(
            "``` source/Example\ntext\n```\n\n",
            "Unresolved relationships: 1\n",
            "word",
            "123",
            "α🙂漢字",
            "!",
            "'",
        )
        for (id in listOf("o200k_base", "cl100k_base")) {
            val estimator = TokenEstimators.create(TokenizerId(id))
            for (suffix in suffixes) {
                for (right in next) {
                    val left = suffix + "\n\n"
                    assertEquals(
                        estimator.count(left + right).value,
                        estimator.count(left).value + estimator.count(right).value,
                        "encoding=$id left=${suffix.toCharArray().map(Char::code)} right=${right.take(8)}",
                    )
                    assertEquals(estimator.count(left + right), estimator.count(listOf(left, right)))
                }
            }
        }
    }

    @Test
    fun `canonical CL100K rejects isolated surrogates outside valid Unicode input`() {
        val estimator = TokenEstimators.create(TokenizerId("cl100k_base"))
        for (surrogate in listOf("\uD800", "\uDC00")) {
            assertFailsWith<AssertionError> { estimator.count(surrogate + "\n\n``` source\n") }
            assertFailsWith<AssertionError> { estimator.count(surrogate + "\n\n") }
            assertFailsWith<AssertionError> { estimator.count(listOf(surrogate + "\n\n", "``` source\n")) }
        }
    }

    @Test
    fun `unsafe boundaries use canonical concatenated counts`() {
        val cases = listOf(
            listOf("a", "b"),
            listOf("word\n\n", " /next"),
            listOf("word\n\n", "/next"),
            listOf("\n", "\nnext"),
            listOf("word\n\n", "\t next"),
            listOf("first\n\n", "", "last"),
        )
        for (id in listOf("o200k_base", "cl100k_base", "chars_4")) {
            val estimator = TokenEstimators.create(TokenizerId(id))
            for (parts in cases) assertEquals(estimator.count(parts.joinToString("")), estimator.count(parts))
        }
    }
}
