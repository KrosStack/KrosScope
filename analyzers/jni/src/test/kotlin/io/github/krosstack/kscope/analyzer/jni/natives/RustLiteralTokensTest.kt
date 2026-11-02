package io.github.krosstack.kscope.analyzer.jni.natives

import io.github.krosstack.kscope.analyzer.jni.lexing.TokenKind
import kotlin.test.Test
import kotlin.test.assertEquals

class RustLiteralTokensTest {
    private fun texts(text: String) = RustTokenizer(text).tokens().map { it.text }

    private fun kinds(text: String) = RustTokenizer(text).tokens().map { it.kind }

    @Test
    fun `a raw string ends at the quote followed by as many hashes as opened it`() {
        val text = "r#\"has \"quotes\" and // slashes\"# x r\"plain\" br##\"a\"#b\"## y"
        assertEquals(
            listOf("r#\"has \"quotes\" and // slashes\"#", "x", "r\"plain\"", "br##\"a\"#b\"##", "y"),
            texts(text),
        )
        assertEquals(
            listOf(TokenKind.STRING, TokenKind.IDENTIFIER, TokenKind.STRING, TokenKind.STRING, TokenKind.IDENTIFIER),
            kinds(text),
        )
    }

    @Test
    fun `byte strings, c strings and byte characters are literals`() {
        val text = "b\"x\" c\"y\" b'z' cr#\"w\"#"
        assertEquals(listOf("b\"x\"", "c\"y\"", "b'z'", "cr#\"w\"#"), texts(text))
        assertEquals(List(4) { TokenKind.STRING }, kinds(text))
    }

    @Test
    fun `a lifetime is punctuation and a character is a literal`() {
        assertEquals(
            listOf("<", "'", "local", ">", "'a'", "'", "static", "&", "'", "a", "str"),
            texts("<'local> 'a' 'static &'a str"),
        )
    }

    @Test
    fun `escaped and quote characters are literals`() {
        val text = "'\\'' '\\n' '\\u{1F600}' '\"' '\\\\' x"
        assertEquals(listOf("'\\''", "'\\n'", "'\\u{1F600}'", "'\"'", "'\\\\'", "x"), texts(text))
        assertEquals(List(5) { TokenKind.STRING } + TokenKind.IDENTIFIER, kinds(text))
    }

    @Test
    fun `a character outside the basic plane is one literal`() {
        assertEquals(listOf("'😀'", "x"), texts("'😀' x"))
    }

    @Test
    fun `a raw identifier is an identifier`() {
        assertEquals(listOf("r#fn", "x"), texts("r#fn x"))
        assertEquals(listOf(TokenKind.IDENTIFIER, TokenKind.IDENTIFIER), kinds("r#fn x"))
    }
}
