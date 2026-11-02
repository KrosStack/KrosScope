package io.github.krosstack.kscope.analyzer.jni.lexing

import io.github.krosstack.kscope.model.TextRange

/** Converts offsets in a text to 1-based lines and columns. */
internal class LineIndex(text: String) {
    private val lineStarts: List<Int> = listOf(0) + text.indices.filter { text[it] == '\n' }.map { it + 1 }

    fun rangeOf(start: Int, end: Int): TextRange {
        val (startLine, startColumn) = positionOf(start)
        val (endLine, endColumn) = positionOf(end)
        return TextRange(startLine, startColumn, endLine, endColumn)
    }

    private fun positionOf(offset: Int): Pair<Int, Int> {
        val line = lineStarts.indexOfLast { it <= offset }
        return (line + 1) to (offset - lineStarts[line] + 1)
    }
}
