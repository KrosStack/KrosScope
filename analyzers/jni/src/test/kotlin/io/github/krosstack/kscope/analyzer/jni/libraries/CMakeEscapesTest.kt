package io.github.krosstack.kscope.analyzer.jni.libraries

import kotlin.test.Test
import kotlin.test.assertEquals

class CMakeEscapesTest {
    private fun names(text: String) = CMakeLibraryReader().read(text).map { it.name }

    @Test
    fun `an escaped parenthesis in an unquoted argument is a literal and keeps the balance`() {
        val text = """
            message(\()
            add_library(first SHARED a.c)
            message(a\)b)
            add_library(second SHARED a.c)
            message(\) \( x)
            add_library(third SHARED a.c)
        """.trimIndent()
        assertEquals(listOf("first", "second", "third"), names(text))
    }

    @Test
    fun `plain nested parentheses still balance`() {
        val text = "message((a))\nadd_library(first SHARED a.c)\nmessage(x (y (z)) w)\nadd_library(second SHARED a.c)"
        assertEquals(listOf("first", "second"), names(text))
    }

    @Test
    fun `an escaped hash starts no comment and an escaped quote starts no quoted argument`() {
        val text = """
            add_library(hash\# SHARED a.c)
            add_library(second SHARED a.c)
            message(a\"b)
            add_library(third SHARED a.c)
            message(a\\) # add_library(comment SHARED a.c)
            add_library(fourth SHARED a.c)
        """.trimIndent()
        assertEquals(listOf("hash\\#", "second", "third", "fourth"), names(text))
    }

    @Test
    fun `escapes inside a quoted argument keep the quote and the backslash apart`() {
        val text = """
            message("a \" ( b")
            add_library(first SHARED a.c)
            message("c \\")
            add_library(second SHARED a.c)
        """.trimIndent()
        assertEquals(listOf("first", "second"), names(text))
    }
}
