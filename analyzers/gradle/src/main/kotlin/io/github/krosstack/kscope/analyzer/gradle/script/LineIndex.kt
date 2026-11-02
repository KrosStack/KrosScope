package io.github.krosstack.kscope.analyzer.gradle.script

import io.github.krosstack.kscope.model.TextRange

/** Converts between offsets in a text and 1-based lines and columns. */
internal class LineIndex(private val text: String) {
    private val lineStarts: List<Int> = listOf(0) + text.indices.filter { text[it] == '\n' }.map { it + 1 }

    fun offsetOf(line: Int, column: Int): Int? {
        val lineStart = lineStarts.getOrNull(line - 1) ?: return null
        return (lineStart + column - 1).takeIf { it in lineStart..text.length }
    }

    fun rangeOf(start: Int, end: Int): TextRange {
        val (startLine, startColumn) = positionOf(start)
        val (endLine, endColumn) = positionOf(end)
        return TextRange(startLine, startColumn, endLine, endColumn)
    }

    fun lineStart(line: Int): Int? = lineStarts.getOrNull(line - 1)

    private fun positionOf(offset: Int): Pair<Int, Int> {
        val line = lineStarts.indexOfLast { it <= offset }
        return (line + 1) to (offset - lineStarts[line] + 1)
    }
}
