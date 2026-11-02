package io.github.krosstack.kscope.analyzer.jni.lexing

/**
 * A position in source text that moves forward one character at a time and knows its line and column, which start at
 * 1. [isLineStart] holds while only white space has been seen since the last line break.
 */
internal class TextCursor(val text: String) {
    var offset = 0
        private set
    var line = 1
        private set
    var column = 1
        private set
    var isLineStart = true
        private set

    val isAtEnd: Boolean get() = offset >= text.length

    fun startsWith(prefix: String): Boolean = text.startsWith(prefix, offset)

    fun advance() {
        val current = text[offset++]
        val isLineEnd = current == '\n' || (current == '\r' && text.getOrNull(offset) != '\n')
        isLineStart = isLineEnd || (isLineStart && current.isWhitespace())
        if (isLineEnd) {
            line++
            column = 1
        } else {
            column++
        }
    }

    fun advanceTo(end: Int) {
        while (offset < end) advance()
    }
}
