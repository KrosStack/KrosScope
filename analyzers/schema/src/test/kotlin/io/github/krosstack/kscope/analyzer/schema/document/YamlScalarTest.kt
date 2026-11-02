package io.github.krosstack.kscope.analyzer.schema.document

import kotlin.test.Test
import kotlin.test.assertEquals

class YamlScalarTest {
    @Test
    fun `quoted keys and values are unquoted and plain values keep their colons`() {
        val text = "\"200\": 'it''s'\n'/tasks/{id}': \"a \\\"b\\\" \\u0041\"\n" +
            "url: http://host:8080/x\nname: a # not b\n"
        val root = readDocument(text)

        assertEquals(listOf("200", "/tasks/{id}", "url", "name"), root.keys())
        assertEquals("it's", root.at("200").scalar())
        assertEquals("a \"b\" A", root.at("/tasks/{id}").scalar())
        assertEquals("http://host:8080/x", root.at("url").scalar())
        assertEquals("a", root.at("name").scalar())
    }

    @Test
    fun `a hash inside quotes is no comment, and comments between and after entries are dropped`() {
        val text = "# header\nfirst: \"a # b\" # trailing\n\n  # indented comment\nsecond: 'c#d'\nthird: e#f\n"
        val root = readDocument(text)

        assertEquals(listOf("first", "second", "third"), root.keys())
        assertEquals("a # b", root.at("first").scalar())
        assertEquals("c#d", root.at("second").scalar())
        assertEquals("e#f", root.at("third").scalar())
    }

    @Test
    fun `block scalars are skipped by indentation and what looks like a key in them is text`() {
        val text = "description: |\n  First line\n  get: not a key\n\n  - not an item\n" +
            "summary: >-\n  folded\n  text\nafter: x\n"
        val root = readDocument(text)

        assertEquals(listOf("description", "summary", "after"), root.keys())
        assertEquals("First line\nget: not a key\n- not an item", root.at("description").scalar())
        assertEquals("folded\ntext", root.at("summary").scalar())
        assertEquals("x", root.at("after").scalar())
        assertEquals(9, lineOf(text, root.entryAt("after").keySpan))
    }

    @Test
    fun `a block scalar in a sequence ends where the indentation falls back`() {
        val text = "notes:\n  - |\n    one\n    two\n  - plain\nend: 1\n"
        val root = readDocument(text)

        assertEquals(listOf("one\ntwo", "plain"), root.at("notes").texts())
        assertEquals("1", root.at("end").scalar())
    }

    @Test
    fun `a value that merely starts with a greater-than sign is no block scalar`() {
        val root = readDocument("range: >=1.0\nnext: 1\n")

        assertEquals(">=1.0", root.at("range").scalar())
        assertEquals("1", root.at("next").scalar())
    }

    @Test
    fun `a plain scalar goes on in the lines below that are indented deeper`() {
        val text = "description: This is long\n  and goes on\n  and on\nnext: 1\n"
        val root = readDocument(text)

        assertEquals("This is long and goes on and on", root.at("description").scalar())
        assertEquals("1", root.at("next").scalar())
    }

    @Test
    fun `a quoted scalar can go on over several lines`() {
        val text = "title: \"one\n  two\"\nnext: 1\n"
        val root = readDocument(text)

        assertEquals("one two", root.at("title").scalar())
        assertEquals("1", root.at("next").scalar())
    }

    @Test
    fun `anchors, tags and merge keys do not hide the value`() {
        val text = "base: &base\n  type: object\nderived:\n  <<: *base\n  name: !!str Task\nanchored: &anchor value\n"
        val root = readDocument(text)

        assertEquals("object", root.at("base", "type").scalar())
        assertEquals("*base", root.at("derived", "<<").scalar())
        assertEquals("Task", root.at("derived", "name").scalar())
        assertEquals("value", root.at("anchored").scalar())
    }
}
