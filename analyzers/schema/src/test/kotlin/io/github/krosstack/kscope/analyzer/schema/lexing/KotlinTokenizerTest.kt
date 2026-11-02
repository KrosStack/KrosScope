package io.github.krosstack.kscope.analyzer.schema.lexing

import kotlin.test.Test
import kotlin.test.assertEquals

private fun tokensOf(text: String): List<String> = KotlinTokenizer(text).tokens().map { it.text }

class KotlinTokenizerTest {
    @Test
    fun `a single-quoted string of Groovy is one token with the double quotes and the templates inside it`() {
        assertEquals(listOf("srcDir", "'a \"b\" \${c}'", "next"), tokensOf("srcDir 'a \"b\" \${c}' next"))
    }

    @Test
    fun `a block in triple apostrophes is one token, whatever lines and quotes it holds`() {
        val text = "script '''line one\n it's \"two\"\n''' after"

        assertEquals(listOf("script", "'''line one\n it's \"two\"\n'''", "after"), tokensOf(text))
    }

    @Test
    fun `a block in triple apostrophes that is never closed ends with the text`() {
        assertEquals(listOf("script", "'''open"), tokensOf("script '''open"))
    }

    @Test
    fun `comments of Groovy and strings with comment signs are not mistaken for each other`() {
        val text = "a 'x // y' b // c 'd'\ne \"/* f */\" /* g 'h' */ i"

        assertEquals(listOf("a", "'x // y'", "b", "e", "\"/* f */\"", "i"), tokensOf(text))
    }

    @Test
    fun `a template with strings inside a double-quoted string stays in the string`() {
        assertEquals(listOf("x", "\"\${a + \"b\"}/c\"", "y"), tokensOf("x \"\${a + \"b\"}/c\" y"))
    }
}
