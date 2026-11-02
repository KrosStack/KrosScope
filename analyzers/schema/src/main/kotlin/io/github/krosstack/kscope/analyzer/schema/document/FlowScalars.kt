package io.github.krosstack.kscope.analyzer.schema.document

import io.github.krosstack.kscope.analyzer.schema.Span

private const val PLAIN_STOPS = ",[]{}"
private val lineBreakRun = Regex("\\s*\n\\s*")

/** Reads the quoted and the plain scalars of flow style, from the position of a cursor. */
internal class FlowScalars(private val cursor: FlowCursor) {
    fun quoted(): DocScalar {
        val text = cursor.text
        val open = cursor.position
        val close = closingQuote(text, open).let { if (it < 0) text.length else it }
        cursor.position = minOf(close + 1, text.length)
        return DocScalar(unquote(text.substring(open + 1, close), text[open]), Span(open + 1, close))
    }

    /** A plain scalar ends at a comma or a bracket, and a key also at its colon. */
    fun plain(isKey: Boolean): DocScalar {
        val text = cursor.text
        val start = cursor.position
        while (!cursor.isAtEnd && !endsPlain(isKey)) cursor.position++
        val raw = text.substring(start, cursor.position).trimEnd()
        return DocScalar(raw.replace(lineBreakRun, " "), Span(start, start + raw.length))
    }

    fun empty() = DocScalar("", Span(cursor.position, cursor.position))

    private fun endsPlain(isKey: Boolean): Boolean {
        val position = cursor.position
        val character = cursor.text[position]
        val isComment = character == '#' && position > 0 && cursor.text[position - 1].isWhitespace()
        return character in PLAIN_STOPS || isComment || (isKey && character == ':')
    }
}
