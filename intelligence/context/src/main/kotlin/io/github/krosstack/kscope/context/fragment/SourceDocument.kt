package io.github.krosstack.kscope.context.fragment

import io.github.krosstack.kscope.model.TextRange

internal class SourceDocument(val text: String) {
    private val starts = listOf(0) + text.indices.filter { text[it] == '\n' }.map { it + 1 }
    val fullRange = TextRange(1, 1, starts.size, text.length - starts.last() + 1)

    fun slice(range: TextRange): String? {
        val start = offset(range.startLine, range.startColumn)
        val end = offset(range.endLine, range.endColumn)
        return if (start != null && end != null && start <= end) text.substring(start, end) else null
    }

    fun offsets(range: TextRange): IntRange? {
        val start = offset(range.startLine, range.startColumn)
        val end = offset(range.endLine, range.endColumn)
        return if (start != null && end != null && start <= end) start until end else null
    }

    private fun offset(line: Int, column: Int): Int? {
        if (line !in 1..starts.size || column < 1) return null
        val start = starts[line - 1]
        val lineEnd = if (line < starts.size) starts[line] - 1 else text.length
        val result = start + column - 1
        val splitPair = result in 1 until text.length && text[result - 1].isHighSurrogate() &&
            text[result].isLowSurrogate()
        return result.takeIf { it <= lineEnd && !splitPair }
    }
}
