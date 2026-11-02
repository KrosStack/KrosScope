package io.github.krosstack.kscope.spi.search

import kotlin.test.Test
import kotlin.test.assertEquals

class CodeTokenizerTest {
    @Test
    fun `a pascal case identifier is kept whole and split into its parts`() {
        assertEquals(listOf("taskrepository", "task", "repository"), CodeTokenizer.tokens("TaskRepository"))
    }

    @Test
    fun `a camel case identifier splits where a lower case letter meets an upper case one`() {
        assertEquals(listOf("gettaskbyid", "get", "task", "by", "id"), CodeTokenizer.tokens("getTaskById"))
    }

    @Test
    fun `an acronym is one part and the capital that starts the next word is not part of it`() {
        assertEquals(listOf("httpserver", "http", "server"), CodeTokenizer.tokens("HTTPServer"))
        assertEquals(listOf("parsexmlfile", "parse", "xml", "file"), CodeTokenizer.tokens("parseXMLFile"))
    }

    @Test
    fun `snake case and kebab case split at their separators and keep the whole`() {
        assertEquals(listOf("maxretrycount", "max", "retry", "count"), CodeTokenizer.tokens("max_retry_count"))
        assertEquals(listOf("kebabcase", "kebab", "case"), CodeTokenizer.tokens("kebab-case"))
        assertEquals(listOf("maxvalue", "max", "value"), CodeTokenizer.tokens("MAX_VALUE"))
    }

    @Test
    fun `digits are split from letters and kept in the whole identifier`() {
        assertEquals(listOf("base64encoder", "base", "64", "encoder"), CodeTokenizer.tokens("Base64Encoder"))
        assertEquals(listOf("utf8", "utf"), CodeTokenizer.tokens("utf8"))
    }

    @Test
    fun `dots and slashes end a word so that a path and a qualified name give their segments`() {
        assertEquals(listOf("com", "example", "foo"), CodeTokenizer.tokens("com.example.Foo"))
        assertEquals(listOf("core", "src", "main", "foo", "kt"), CodeTokenizer.tokens("core/src/main/Foo.kt"))
    }

    @Test
    fun `terms shorter than two characters are dropped`() {
        assertEquals(emptyList(), CodeTokenizer.tokens("a b c x"))
        assertEquals(listOf("ab"), CodeTokenizer.tokens("a_b"))
        assertEquals(listOf("r2d2"), CodeTokenizer.tokens("R2D2"))
        assertEquals(listOf("id"), CodeTokenizer.tokens("id"))
    }

    @Test
    fun `prose is lowercased and stripped of punctuation`() {
        assertEquals(listOf("hello", "world", "it", "works"), CodeTokenizer.tokens("Hello, WORLD! (It works.)"))
    }

    @Test
    fun `letters beyond ascii are letters`() {
        assertEquals(listOf("äpfelsaft", "äpfel", "saft"), CodeTokenizer.tokens("ÄpfelSaft"))
        assertEquals(listOf("größe"), CodeTokenizer.tokens("Größe"))
    }

    @Test
    fun `the whole identifiers and the parts are available apart`() {
        assertEquals(listOf("taskrepository", "save"), CodeTokenizer.identifiers("TaskRepository.save"))
        assertEquals(listOf("task", "repository", "save"), CodeTokenizer.parts("TaskRepository.save"))
        assertEquals("taskrepository task repository", CodeTokenizer.indexText("TaskRepository"))
        assertEquals("", CodeTokenizer.indexText(null))
        assertEquals("", CodeTokenizer.indexText("   "))
    }
}
