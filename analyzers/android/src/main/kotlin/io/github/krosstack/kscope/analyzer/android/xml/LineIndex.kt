package io.github.krosstack.kscope.analyzer.android.xml

import io.github.krosstack.kscope.model.TextRange

/** Converts offsets of a text to the 1-based line and column of the model; `\r\n`, `\n` and `\r` end a line. */
internal class LineIndex(text: String) {
    private val lineStarts: IntArray = lineStartsOf(text)

    fun offsetOf(line: Int, column: Int): Int = lineStarts[(line - 1).coerceIn(0, lineStarts.lastIndex)] + column - 1

    fun range(start: Int, end: Int): TextRange {
        val (startLine, startColumn) = positionOf(start)
        val (endLine, endColumn) = positionOf(end)
        return TextRange(startLine, startColumn, endLine, endColumn)
    }

    private fun positionOf(offset: Int): Pair<Int, Int> {
        val found = lineStarts.binarySearch(offset)
        val index = if (found >= 0) found else -found - 2
        return index + 1 to offset - lineStarts[index] + 1
    }

    private companion object {
        fun lineStartsOf(text: String): IntArray {
            val starts = mutableListOf(0)
            var index = 0
            while (index < text.length) {
                if (text[index] == '\r' && text.getOrNull(index + 1) == '\n') index++
                if (text[index] == '\n' || text[index] == '\r') starts += index + 1
                index++
            }
            return starts.toIntArray()
        }
    }
}
