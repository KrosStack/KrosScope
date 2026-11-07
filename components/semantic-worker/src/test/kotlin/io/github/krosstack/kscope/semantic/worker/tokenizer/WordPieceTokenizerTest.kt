package io.github.krosstack.kscope.semantic.worker.tokenizer

import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordPieceTokenizerTest {
    private val tokenizer = WordPieceTokenizer(
        TokenizerFile(java.nio.file.Path.of(requireNotNull(javaClass.getResource("/tokenizer.json")).toURI())),
        8192,
    )

    @Test
    fun `pinned Python encodings match including truncation and newer Unicode`() {
        val fixtures = KscopeJson.parseToJsonElement(
            requireNotNull(javaClass.getResource("/expected-tokens.json")).readText(),
        ).jsonArray
        assertEquals(33, fixtures.size)
        fixtures.forEach { fixture ->
            val text = fixture.jsonObject.getValue("text").jsonPrimitive.content
            val expected = fixture.jsonObject.getValue("ids").jsonArray.map { it.jsonPrimitive.int.toLong() }
            assertEquals(expected, tokenizer.encode(text).ids.toList(), text.take(100))
        }
    }

    @Test
    fun `truncation keeps the final separator at maximum length`() {
        val ids = tokenizer.encode("hello ".repeat(8300)).ids
        assertEquals(8192, ids.size)
        assertEquals(102L, ids.last())
    }

    @Test
    fun `cap keeps a complete vocabulary word within the token budget`() {
        assertEquals("implementation", tokenizer.cap("implementation hello world", 3))
    }

    @Test
    fun `cap keeps a word joined by a removed control character`() {
        assertEquals("imple\u001cmentation", tokenizer.cap("imple\u001cmentation hello world", 3))
    }

    @Test
    fun `cap keeps special tokens intact`() {
        assertEquals("hello [CLS]", tokenizer.cap("hello [CLS] world", 4))
    }

    @Test
    fun `cap maps normalized accents back to the original prefix`() {
        assertEquals("caf\u00e9", tokenizer.cap("caf\u00e9 hello world", 3))
    }

    @Test
    fun `cap falls back to a fitting prefix of the first word`() {
        assertEquals("una", tokenizer.cap("unaffable hello", 3))
    }

    @Test
    fun `cap preserves an original Unicode prefix inside the token budget`() {
        val source = "😀 café hello world ".repeat(50)
        val capped = tokenizer.cap(source, 20)
        assertTrue(capped.isNotEmpty() && source.startsWith(capped))
        assertTrue(tokenizer.encode(capped, Int.MAX_VALUE).ids.size <= 20)
    }
}
