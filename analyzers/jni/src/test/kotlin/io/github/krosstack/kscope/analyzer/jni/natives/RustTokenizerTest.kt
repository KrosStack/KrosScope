package io.github.krosstack.kscope.analyzer.jni.natives

import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class RustTokenizerTest {
    private fun texts(text: String) = RustTokenizer(text).tokens().map { it.text }

    @Test
    fun `line comments and nested block comments are dropped`() {
        val text = "a // fn Java_x() {}\n/* outer /* inner fn Java_y() {} */ still a comment */ b /// doc\n//! inner\n"
        assertEquals(listOf("a", "b"), texts(text))
    }

    @Test
    fun `a string is one token whatever it holds`() {
        val text = "x \"a \\\" b // c /* d\" y \"two\n  lines\" z"
        assertEquals(listOf("x", "\"a \\\" b // c /* d\"", "y", "\"two\n  lines\"", "z"), texts(text))
    }

    @Test
    fun `numbers keep their suffix and a range is not a fraction`() {
        assertEquals(listOf("0xFF_u64", "1", ".", ".", "2", "1.5e3"), texts("0xFF_u64 1..2 1.5e3"))
    }

    @Test
    fun `a token knows where it starts and ends`() {
        val tokens = RustTokenizer("fn a() {}\n  b").tokens()
        assertEquals(TextRange(1, 4, 1, 5), tokens[1].range)
        assertEquals(TextRange(2, 3, 2, 4), tokens.last().range)
    }

    @Test
    fun `an unterminated string or comment ends at the end of the file`() {
        assertEquals(listOf("x", "\"abc"), texts("x \"abc"))
        assertEquals(listOf("x"), texts("x /* abc /* def */"))
        assertEquals(listOf("x", "r#\"abc"), texts("x r#\"abc"))
    }
}
