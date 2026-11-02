package io.github.krosstack.kscope.analyzer.jni.libraries

/** A logical line of a makefile: its text without comment, and the offsets of its first line and of its end. */
internal class MakeLine(val text: String, val start: Int, val end: Int)

/** Splits a makefile into logical lines: a trailing backslash joins the next line, and a `#` starts a comment. */
internal object MakeLines {
    fun of(text: String): List<MakeLine> {
        val lines = mutableListOf<MakeLine>()
        var offset = 0
        while (offset < text.length) {
            val line = logicalLineAt(text, offset)
            lines += line
            offset = line.end + 1
        }
        return lines
    }

    private fun logicalLineAt(text: String, start: Int): MakeLine {
        val content = StringBuilder()
        var lineStart = start
        var lineEnd: Int
        do {
            lineEnd = text.indexOf('\n', lineStart).takeIf { it >= 0 } ?: text.length
            val physical = text.substring(lineStart, lineEnd).trimEnd('\r')
            val isContinued = physical.endsWith('\\') && lineEnd < text.length
            content.append(if (isContinued) physical.dropLast(1) + " " else physical)
            lineStart = lineEnd + 1
        } while (isContinued)
        return MakeLine(content.toString().substringBefore('#'), start, lineEnd)
    }
}
