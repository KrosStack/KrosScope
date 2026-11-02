package io.github.krosstack.kscope.analyzer.schema.document

import io.github.krosstack.kscope.analyzer.schema.Span

private const val VALUE_ENDS = ",]}"
private const val MAX_DEPTH = 200

/**
 * Reads the value that starts at an offset of a text in flow style: `[a, b]`, `{a: b}`, a quoted or a plain scalar.
 * JSON is flow style, so this reads a JSON document as well. It never fails: text that is not well formed ends a
 * collection early or reads as a scalar.
 */
internal class FlowReader(text: String, start: Int) {
    private val cursor = FlowCursor(text, start)
    private val scalars = FlowScalars(cursor)
    private var depth = 0

    fun read(): DocNode {
        cursor.skipSpace()
        return when (cursor.peek()) {
            '{' -> nested(::mapping)
            '[' -> nested(::sequence)
            '"', '\'' -> scalars.quoted()
            else -> scalars.plain(isKey = false)
        }
    }

    private fun nested(reader: () -> DocNode): DocNode {
        if (depth >= MAX_DEPTH) return scalars.empty()
        depth++
        val node = reader()
        depth--
        return node
    }

    private fun mapping(): DocMapping {
        val start = cursor.position++
        val entries = ArrayList<DocEntry>()
        while (!closes('}')) {
            val before = cursor.position
            entries += entry()
            if (cursor.position == before) cursor.position++
        }
        return DocMapping(entries, Span(start, cursor.position))
    }

    private fun entry(): DocEntry {
        val isQuoted = cursor.peek() == '"' || cursor.peek() == '\''
        val key = if (isQuoted) scalars.quoted() else scalars.plain(isKey = true)
        cursor.skipSpace()
        if (cursor.peek() == ':') cursor.position++
        cursor.skipSpace()
        val value = if (cursor.peek() in VALUE_ENDS || cursor.isAtEnd) scalars.empty() else read()
        return DocEntry(key.text, key.span, value)
    }

    private fun sequence(): DocSequence {
        val start = cursor.position++
        val items = ArrayList<DocNode>()
        while (!closes(']')) {
            val before = cursor.position
            items += read()
            if (cursor.position == before) cursor.position++
        }
        return DocSequence(items, Span(start, cursor.position))
    }

    private fun closes(closing: Char): Boolean {
        cursor.skipSpace()
        while (cursor.peek() == ',') {
            cursor.position++
            cursor.skipSpace()
        }
        val isClosing = cursor.isAtEnd || cursor.peek() == closing
        if (isClosing && !cursor.isAtEnd) cursor.position++
        return isClosing
    }
}
