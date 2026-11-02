package io.github.krosstack.kscope.analyzer.schema.document

private const val END = '\u0000'

/** A position in a text read in flow style, which moves past white space and comments. */
internal class FlowCursor(val text: String, start: Int) {
    var position = start

    val isAtEnd: Boolean get() = position >= text.length

    /** The character at the position; a null character at the end. */
    fun peek(): Char = text.getOrElse(position) { END }

    fun skipSpace() {
        while (!isAtEnd && (text[position].isWhitespace() || isCommentStart())) {
            position = if (isCommentStart()) endOfLine() else position + 1
        }
    }

    private fun isCommentStart(): Boolean =
        text[position] == '#' && (position == 0 || text[position - 1].isWhitespace())

    private fun endOfLine(): Int {
        val end = text.indexOf('\n', position)
        return if (end < 0) text.length else end
    }
}
