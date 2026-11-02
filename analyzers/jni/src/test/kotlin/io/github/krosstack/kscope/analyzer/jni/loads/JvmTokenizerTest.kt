package io.github.krosstack.kscope.analyzer.jni.loads

import io.github.krosstack.kscope.analyzer.jni.lexing.TokenKind
import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

private const val RAW = "\"\"\""

class JvmTokenizerTest {
    private fun kotlin(text: String) = JvmTokenizer(text, JvmDialect.KOTLIN).tokens().map { it.text }

    private fun java(text: String) = JvmTokenizer(text, JvmDialect.JAVA).tokens().map { it.text }

    @Test
    fun `comments are dropped and block comments nest in kotlin only`() {
        val text = "a // line\n/* outer /* inner */ still */ b"
        assertEquals(listOf("a", "b"), kotlin(text))
        assertEquals(listOf("a", "still", "*", "/", "b"), java(text))
    }

    @Test
    fun `a string is one token with its escapes`() {
        val text = "x \"a \\\" // b\" y '\"' z '\\'' w"
        assertEquals(listOf("x", "\"a \\\" // b\"", "y", "'\"'", "z", "'\\''", "w"), kotlin(text))
        assertEquals(kotlin(text), java(text))
    }

    @Test
    fun `a kotlin raw string has no escapes and a java text block has`() {
        val text = "x " + RAW + "a \\" + RAW + " b " + RAW + "c" + RAW + " y"
        assertEquals(listOf("x", RAW + "a \\" + RAW, "b", RAW + "c" + RAW, "y"), kotlin(text))
        assertEquals(listOf("x", RAW + "a \\" + RAW + " b " + RAW, "c", RAW + " y"), java(text))
    }

    @Test
    fun `a kotlin template keeps the strings inside its braces in the string around it`() {
        val text = "x \"before \${f(\"inner\") + g(\"{\")} after\" y"
        assertEquals(listOf("x", "\"before \${f(\"inner\") + g(\"{\")} after\"", "y"), kotlin(text))
    }

    @Test
    fun `a dollar sign starts no template in java`() {
        val text = "\"a\${\" b \"}\""
        assertEquals(listOf("\"a\${\"", "b", "\"}\""), java(text))
        assertEquals(listOf(text), kotlin(text))
    }

    @Test
    fun `a string that is not closed ends at the end of the line`() {
        assertEquals(listOf("x", "\"open", "y"), kotlin("x \"open\ny"))
    }

    @Test
    fun `quoted identifiers, numbers and punctuation`() {
        assertEquals(listOf("`my fun`", "(", "1_000L", ")", ".", "5", "0.5f"), kotlin("`my fun`(1_000L).5 0.5f"))
    }

    @Test
    fun `a token knows where it starts and ends`() {
        val tokens = JvmTokenizer("val a = 1\n  b", JvmDialect.KOTLIN).tokens()
        assertEquals(TextRange(1, 5, 1, 6), tokens[1].range)
        assertEquals(TextRange(2, 3, 2, 4), tokens.last().range)
        assertEquals(TokenKind.NUMBER, tokens[3].kind)
    }
}
