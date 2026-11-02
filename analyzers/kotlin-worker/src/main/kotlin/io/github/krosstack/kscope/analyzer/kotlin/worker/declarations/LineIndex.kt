package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import io.github.krosstack.kscope.model.TextRange

/** Converts character offsets of one file into 1-based lines and columns. */
internal class LineIndex(text: CharSequence) {
    private val lineStarts: IntArray = buildList {
        add(0)
        text.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
    }.toIntArray()

    fun range(startOffset: Int, endOffset: Int): TextRange {
        val (startLine, startColumn) = position(startOffset)
        val (endLine, endColumn) = position(endOffset)
        return TextRange(startLine, startColumn, endLine, endColumn)
    }

    private fun position(offset: Int): Pair<Int, Int> {
        val found = lineStarts.binarySearch(offset)
        val lineIndex = if (found >= 0) found else -found - 2
        return (lineIndex + 1) to (offset - lineStarts[lineIndex] + 1)
    }
}
