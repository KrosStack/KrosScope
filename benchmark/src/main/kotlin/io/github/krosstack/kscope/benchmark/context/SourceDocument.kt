package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.TextRange

internal data class SourceInterval(val start: Int, val end: Int)
internal data class SourceSpan(val artifact: ArtifactRef, val interval: SourceInterval)

internal class SourceDocument(val artifact: ArtifactRef, val text: String) {
    private val lineStarts = buildList {
        add(0)
        text.forEachIndexed { index, character -> if (character == '\n') add(index + 1) }
    }.toIntArray()

    fun interval(range: TextRange): SourceInterval? {
        val start = offset(range.startLine, range.startColumn)
        val end = offset(range.endLine, range.endColumn)
        return if (start != null && end != null && validInterval(start, end)) {
            SourceInterval(start, end)
        } else {
            null
        }
    }

    fun range(interval: SourceInterval): TextRange {
        require(interval.start in 0..interval.end && interval.end <= text.length) { "Invalid source interval." }
        require(boundary(interval.start) && boundary(interval.end)) { "Source interval splits a Unicode character." }
        val startLine = lineIndex(interval.start)
        val endLine = lineIndex(interval.end)
        return TextRange(
            startLine + 1,
            interval.start - lineStarts[startLine] + 1,
            endLine + 1,
            interval.end - lineStarts[endLine] + 1,
        )
    }

    fun fullRange(): TextRange = range(SourceInterval(0, text.length))

    private fun validInterval(start: Int, end: Int): Boolean = start <= end && boundary(start) && boundary(end)

    private fun offset(line: Int, column: Int): Int? {
        val start = lineStarts.getOrNull(line - 1) ?: return null
        val next = lineStarts.getOrNull(line) ?: (text.length + 1)
        val offset = start + column - 1
        return offset.takeIf { column > 0 && it < next && it <= text.length }
    }

    private fun boundary(offset: Int): Boolean = offset == 0 || offset == text.length ||
        !(text[offset - 1].isHighSurrogate() && text[offset].isLowSurrogate())

    private fun lineIndex(offset: Int): Int {
        val found = lineStarts.binarySearch(offset)
        return if (found >= 0) found else -found - 2
    }
}
