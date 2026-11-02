package io.github.krosstack.kscope.analyzer.schema.lexing

/** A position in source text that moves forward through it. */
internal class SourceCursor(val text: String) {
    var offset = 0
        private set

    val isAtEnd: Boolean get() = offset >= text.length

    val current: Char get() = text[offset]

    fun startsWith(prefix: String): Boolean = text.startsWith(prefix, offset)

    fun advance() {
        offset++
    }

    fun advanceTo(end: Int) {
        offset = maxOf(offset, minOf(end, text.length))
    }
}
