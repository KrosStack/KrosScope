package io.github.krosstack.kscope.analyzer.schema.document

import io.github.krosstack.kscope.analyzer.schema.Span

/**
 * Reads the values of a YAML text that are not block collections. Each starts at a column of a line that the cursor
 * has moved past, and leaves the cursor past the last line that the value takes.
 */
internal class YamlScalarReader(private val text: String, private val cursor: YamlCursor) {
    /** A plain scalar and the lines below that continue it: those indented deeper than its [parent]. */
    fun plain(line: YamlLine, column: Int, parent: Int): DocScalar {
        val first = withoutComment(line.text.substring(column))
        val start = line.start + column
        val parts = arrayListOf(first)
        var end = start + first.length
        while (continues(parent)) {
            val more = checkNotNull(cursor.current())
            val part = withoutComment(more.text)
            parts += part
            end = more.start + part.length
            cursor.advance()
        }
        return DocScalar(parts.joinToString(" "), Span(start, end))
    }

    /** A quoted scalar, which may go on over several lines. */
    fun quoted(line: YamlLine, column: Int): DocScalar {
        val open = line.start + column
        val close = closingQuote(text, open)
        if (close < 0) return DocScalar(text.substring(open + 1, line.end), Span(open + 1, line.end))
        cursor.skipPast(close)
        return DocScalar(unquote(text.substring(open + 1, close), text[open]), Span(open + 1, close))
    }

    /** A collection in flow style, which may go on over several lines. */
    fun flow(line: YamlLine, column: Int): DocNode {
        val node = FlowReader(text, line.start + column).read()
        cursor.skipPast(maxOf(node.span.end - 1, line.start))
        return node
    }

    /** A `|` or `>` scalar: the lines below that are blank or indented deeper than its [parent]. */
    fun blockScalar(line: YamlLine, column: Int, parent: Int): DocScalar {
        val body = ArrayList<String>()
        var end = line.end
        var next = cursor.current()
        while (next != null && (next.text.isEmpty() || next.indent > parent)) {
            if (next.text.isNotEmpty()) {
                body += next.text
                end = next.end
            }
            cursor.advance()
            next = cursor.current()
        }
        return DocScalar(body.joinToString("\n"), Span(line.start + column, end))
    }

    private fun continues(parent: Int): Boolean =
        cursor.current()?.let { !it.isSkippable && it.indent > parent } == true
}
