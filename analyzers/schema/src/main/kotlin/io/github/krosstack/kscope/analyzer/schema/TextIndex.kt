package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.model.TextRange

/** A half-open range of character offsets in a text. */
internal data class Span(val start: Int, val end: Int) {
    companion object {
        val EMPTY = Span(0, 0)
    }
}

/** Converts between character offsets and the 1-based lines and columns that facts use. */
internal class TextIndex(private val text: String) {
    private val lineStarts: List<Int> = buildList {
        add(0)
        text.forEachIndexed { index, character -> if (character == '\n') add(index + 1) }
    }

    fun offsetOf(line: Int, column: Int): Int {
        val lineStart = lineStarts.getOrElse(line - 1) { text.length }
        return (lineStart + column - 1).coerceIn(0, text.length)
    }

    fun lineOf(offset: Int): Int {
        val found = lineStarts.binarySearch(offset.coerceIn(0, text.length))
        return if (found >= 0) found + 1 else -found - 1
    }

    fun range(span: Span): TextRange {
        val startLine = lineOf(span.start)
        val endLine = lineOf(span.end)
        return TextRange(
            startLine = startLine,
            startColumn = span.start - lineStarts[startLine - 1] + 1,
            endLine = endLine,
            endColumn = span.end - lineStarts[endLine - 1] + 1,
        )
    }
}
