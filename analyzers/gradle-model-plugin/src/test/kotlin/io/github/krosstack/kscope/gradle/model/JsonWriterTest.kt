package io.github.krosstack.kscope.gradle.model

import kotlin.test.Test
import kotlin.test.assertEquals

class JsonWriterTest {
    @Test
    fun `scalars and null are written as JSON literals`() {
        assertEquals("null", JsonWriter.write(null))
        assertEquals("true", JsonWriter.write(true))
        assertEquals("42", JsonWriter.write(42))
        assertEquals("\"text\"", JsonWriter.write("text"))
    }

    @Test
    fun `maps and lists nest and keep their order`() {
        val document = linkedMapOf("b" to listOf(1, 2), "a" to linkedMapOf("x" to null, "y" to emptyList<Int>()))
        assertEquals("{\"b\":[1,2],\"a\":{\"x\":null,\"y\":[]}}", JsonWriter.write(document))
    }

    @Test
    fun `quotes backslashes and control characters are escaped`() {
        assertEquals("\"a\\\"b\\\\c\\n\\t\\u0001\"", JsonWriter.write("a\"b\\c\n\t\u0001"))
    }

    @Test
    fun `other objects are written as their text`() {
        assertEquals("\"1.5\"", JsonWriter.write(StringBuilder("1.5")))
    }
}
