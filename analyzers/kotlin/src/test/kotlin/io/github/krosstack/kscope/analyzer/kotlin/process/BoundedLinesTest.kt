package io.github.krosstack.kscope.analyzer.kotlin.process

import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals

class BoundedLinesTest {
    private fun split(text: String, limit: Int = 10): Pair<List<String>, List<String>> {
        val lines = mutableListOf<String>()
        val overlong = mutableListOf<String>()
        BoundedLines(StringReader(text), limit).forEachLine(lines::add, overlong::add)
        return lines to overlong
    }

    @Test
    fun `lines are split at line feeds and a carriage return before one is dropped`() {
        assertEquals(listOf("a", "bb", "", "c") to emptyList(), split("a\nbb\r\n\nc\n"))
    }

    @Test
    fun `a last line without a terminator is a line and an empty text has none`() {
        assertEquals(listOf("a", "b") to emptyList(), split("a\nb"))
        assertEquals(emptyList<String>() to emptyList(), split(""))
    }

    @Test
    fun `a line of exactly the limit is kept`() {
        assertEquals(listOf("0123456789") to emptyList(), split("0123456789\n"))
    }

    @Test
    fun `a line over the limit is reported by its start and the lines around it are intact`() {
        val (lines, overlong) = split("before\n" + "x".repeat(50) + "\nafter\n")
        assertEquals(listOf("before", "after"), lines)
        assertEquals(listOf("x".repeat(50)), overlong)
    }

    @Test
    fun `the start of an overlong line is cut to the preview length`() {
        val (_, overlong) = split("y".repeat(BoundedLines.PREVIEW * 3) + "\n", limit = BoundedLines.PREVIEW * 2)
        assertEquals(listOf("y".repeat(BoundedLines.PREVIEW)), overlong)
    }

    @Test
    fun `an overlong line at the end of the text is reported too`() {
        assertEquals(listOf("ok") to listOf("z".repeat(20)), split("ok\n" + "z".repeat(20), limit = 12))
    }

    @Test
    fun `a line that spans several reads is joined`() {
        val long = "k".repeat(20_000)
        assertEquals(listOf(long, "tail") to emptyList(), split("$long\ntail\n", limit = 30_000))
    }
}
