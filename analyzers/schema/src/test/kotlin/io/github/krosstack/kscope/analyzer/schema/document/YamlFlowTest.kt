package io.github.krosstack.kscope.analyzer.schema.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class YamlFlowTest {
    @Test
    fun `flow sequences and mappings are read and keep their lines`() {
        val text = "types: [string, 'two words', \"q\"]\nempty: []\nnone: {}\nmap: {a: 1, b: [x, y]}\nlater: yes\n"
        val root = readDocument(text)

        assertEquals(listOf("string", "two words", "q"), root.at("types").texts())
        assertEquals(0, root.at("empty").items().size)
        assertEquals(0, (root.at("none") as DocMapping).entries.size)
        assertEquals("1", root.at("map", "a").scalar())
        assertEquals(listOf("x", "y"), root.at("map", "b").texts())
        assertEquals("yes", root.at("later").scalar())
        assertEquals(4, lineOf(text, root.at("map", "b").span))
    }

    @Test
    fun `a flow collection can go on over several lines`() {
        val text = "required: [\n  id,\n  title,\n]\nnext: value\n"
        val root = readDocument(text)

        assertEquals(listOf("id", "title"), root.at("required").texts())
        assertEquals("value", root.at("next").scalar())
        assertEquals(5, lineOf(text, root.entryAt("next").keySpan))
    }

    @Test
    fun `a comment inside a flow collection is skipped`() {
        val root = readDocument("list: [a, # first\n  b]\nnext: 1\n")

        assertEquals(listOf("a", "b"), root.at("list").texts())
        assertEquals("1", root.at("next").scalar())
    }

    @Test
    fun `nesting that is far too deep is cut off instead of overflowing the stack`() {
        val brackets = "a: " + "[".repeat(5000) + "x" + "]".repeat(5000) + "\nnext: 1\n"
        val braces = "{\"a\":".repeat(5000) + "1" + "}".repeat(5000)
        val dashes = "- ".repeat(5000) + "x\n"

        assertTrue(YamlReader(brackets).read() != null)
        assertTrue(YamlReader(braces).read() != null)
        assertTrue(YamlReader(dashes).read() != null)
    }

    @Test
    fun `text that is not well formed is read without failing`() {
        val broken = listOf(
            "key: [unclosed, list\nnext: 1\n",
            "a:\n    b: 1\n  c: 2\n",
            "\"open: 1\n",
            "- : :\n- - -\n",
            "{ : , ] }\n",
        )
        broken.forEach { text -> assertTrue(YamlReader(text).read() != null, text) }
        assertIs<DocMapping>(readDocument("a:\n    b: 1\n  c: 2\n"))
    }
}
