package io.github.krosstack.kscope.analyzer.schema.document

import io.github.krosstack.kscope.analyzer.schema.TextIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YamlStructureTest {
    private val specification = """
        openapi: 3.0.3
        info:
          title: TaskTide
          version: 1.0.0
        paths:
          /tasks:
            get:
              operationId: listTasks
              tags:
                - Tasks
              responses:
                "200":
                  description: The tasks, newest first.
    """.trimIndent() + "\n"

    @Test
    fun `mappings are read by indentation and keep the line of every key and value`() {
        val root = readDocument(specification)
        val operation = root.at("paths", "/tasks", "get")

        assertEquals(listOf("openapi", "info", "paths"), root.keys())
        assertEquals("3.0.3", root.at("openapi").scalar())
        assertEquals("TaskTide", root.at("info", "title").scalar())
        assertEquals(1, lineOf(specification, root.entryAt("openapi").keySpan))
        assertEquals(3, lineOf(specification, root.at("info", "title").span))
        assertEquals(7, lineOf(specification, root.entryAt("paths", "/tasks", "get").keySpan))
        assertEquals(8, lineOf(specification, root.entryAt("paths", "/tasks", "get", "operationId").keySpan))
        assertEquals(8, lineOf(specification, operation.at("operationId").span))
        assertEquals("listTasks", operation.at("operationId").scalar())
    }

    @Test
    fun `the span of a scalar is its text and the span of an entry runs to the end of its value`() {
        val root = readDocument(specification)
        val identifier = root.entryAt("paths", "/tasks", "get", "operationId")
        val get = root.entryAt("paths", "/tasks", "get")

        assertEquals("listTasks", specification.substring(identifier.value.span.start, identifier.value.span.end))
        assertEquals("operationId", specification.substring(identifier.keySpan.start, identifier.keySpan.end))
        assertEquals(7 to 13, lineOf(specification, get.span) to TextIndex(specification).lineOf(get.span.end))
    }

    @Test
    fun `a sequence is read with its items, indented below the key or at the indentation of the key`() {
        val text = "tags:\n  - one\n  - two\nroles:\n- admin\n- user\nafter: done\n"
        val root = readDocument(text)

        assertEquals(listOf("one", "two"), root.at("tags").texts())
        assertEquals(listOf("admin", "user"), root.at("roles").texts())
        assertEquals("done", root.at("after").scalar())
    }

    @Test
    fun `an item of a sequence can be a mapping that goes on in the lines below`() {
        val text = "parameters:\n  - name: id\n    in: path\n    required: true\n  - name: limit\n    in: query\n"
        val parameters = readDocument(text).at("parameters").items()

        assertEquals(2, parameters.size)
        assertEquals(listOf("name", "in", "required"), parameters[0].keys())
        assertEquals("query", parameters[1].at("in").scalar())
        assertEquals(5, lineOf(text, parameters[1].span))
    }

    @Test
    fun `a sequence can hold sequences and an item can hold the mapping of a nested dash`() {
        val text = "matrix:\n  - - a\n    - b\n  - - c\nlist:\n  -\n    key: value\n"
        val root = readDocument(text)

        assertEquals(listOf(listOf("a", "b"), listOf("c")), root.at("matrix").items().map { it.texts() })
        assertEquals("value", root.at("list").items().single().at("key").scalar())
    }

    @Test
    fun `an entry with no value is empty and the next key is a sibling`() {
        val root = readDocument("first:\nsecond: 2\nthird:\n")

        assertEquals("", root.at("first").scalar())
        assertEquals("2", root.at("second").scalar())
        assertEquals("", root.at("third").scalar())
    }

    @Test
    fun `only the first document of a stream is read and a byte order mark and windows line breaks are fine`() {
        val text = "\uFEFF---\r\nfirst: 1\r\nlist:\r\n  - a\r\n---\r\nsecond: 2\r\n"
        val root = readDocument(text)

        assertEquals(listOf("first", "list"), root.keys())
        assertEquals(listOf("a"), root.at("list").texts())
        assertEquals(3, lineOf(text, root.entryAt("list").keySpan))
    }

    @Test
    fun `a marker followed by white space or a comment starts and ends a document like a bare marker`() {
        val text = "--- # API description\nfirst: 1\nlist:\n  - a\n... # end\nsecond: 2\n"
        val root = readDocument(text)

        assertEquals(listOf("first", "list"), root.keys())
        assertEquals(listOf("a"), root.at("list").texts())
        assertEquals(listOf("first"), readDocument("---\t# note\nfirst: 1\n--- \nsecond: 2\n").keys())
        assertEquals(listOf("first"), readDocument("--- \nfirst: 1\n...  \nsecond: 2\n").keys())
    }

    @Test
    fun `text that only begins like a marker is content`() {
        assertEquals("---foo", readDocument("---foo\n").scalar())
        assertEquals("...foo", readDocument("...foo\n").scalar())
    }

    @Test
    fun `a text with no content reads as nothing`() {
        assertNull(YamlReader("").read())
        assertNull(YamlReader("# only a comment\n\n").read())
    }

    @Test
    fun `a lone scalar is a document`() {
        assertEquals("just text", readDocument("just text\n").scalar())
    }
}
