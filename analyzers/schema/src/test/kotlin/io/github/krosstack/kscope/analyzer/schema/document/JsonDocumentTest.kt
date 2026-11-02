package io.github.krosstack.kscope.analyzer.schema.document

import io.github.krosstack.kscope.analyzer.schema.TextIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JsonDocumentTest {
    private val document = """
        {
          "openapi": "3.0.3",
          "info": {"title": "TaskTide", "version": "1.0.0"},
          "paths": {
            "/tasks/{id}": {
              "get": {
                "operationId": "getTask",
                "tags": ["Tasks", "Reading"],
                "deprecated": false,
                "x-rank": 7,
                "x-owner": null
              }
            }
          }
        }
    """.trimIndent() + "\n"

    private fun lineOf(node: DocNode) = TextIndex(document).lineOf(node.span.start)

    @Test
    fun `a JSON document is read as nested mappings and sequences with the line of every value`() {
        val root = readDocument(document)

        assertEquals("3.0.3", root.at("openapi").scalar())
        assertEquals("TaskTide", root.at("info", "title").scalar())
        assertEquals("getTask", root.at("paths", "/tasks/{id}", "get", "operationId").scalar())
        val tags = (root.at("paths", "/tasks/{id}", "get", "tags") as DocSequence).items
        assertEquals(listOf("Tasks", "Reading"), tags.map { it.scalar() })
        assertEquals(2, lineOf(root.at("openapi")))
        assertEquals(3, lineOf(root.at("info", "title")))
        assertEquals(7, lineOf(root.at("paths", "/tasks/{id}", "get", "operationId")))
    }

    @Test
    fun `numbers, booleans and null are scalars and keys are the text without quotes`() {
        val operation = readDocument(document).at("paths", "/tasks/{id}", "get")

        assertEquals("false", operation.at("deprecated").scalar())
        assertEquals("7", operation.at("x-rank").scalar())
        assertEquals("null", operation.at("x-owner").scalar())
        val keys = (operation as DocMapping).entries.map { it.key }
        assertEquals(listOf("operationId", "tags", "deprecated", "x-rank", "x-owner"), keys)
    }

    @Test
    fun `escapes in strings are decoded and a string can hold braces and colons`() {
        val text = "{\"a\": \"line\\nbreak \\\"quoted\\\" \\u00e9\", \"b\": \"{not: [a, collection]}\", \"c\\\\d\": 1}"
        val root = readDocument(text)

        assertEquals("line\nbreak \"quoted\" \u00e9", root.at("a").scalar())
        assertEquals("{not: [a, collection]}", root.at("b").scalar())
        assertEquals("1", root.at("c\\d").scalar())
    }

    @Test
    fun `a compact document on one line and an empty object are read`() {
        val root = readDocument("{\"openapi\":\"3.1.0\",\"paths\":{},\"servers\":[{\"url\":\"/v1\"}]}")

        assertEquals("3.1.0", root.at("openapi").scalar())
        assertEquals(0, (root.at("paths") as DocMapping).entries.size)
        assertEquals("/v1", (root.at("servers") as DocSequence).items.single().at("url").scalar())
    }

    @Test
    fun `a document that is cut off is read as far as it goes`() {
        val root = readDocument("{\"openapi\": \"3.0.0\", \"info\": {\"title\": \"Cut")

        assertEquals("3.0.0", root.at("openapi").scalar())
        assertIs<DocMapping>(root.at("info"))
    }
}
