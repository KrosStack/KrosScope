package io.github.krosstack.kscope.analyzer.jni.libraries

/**
 * Takes the comments out of CMake text by turning them into spaces, so that offsets and lines stay what they were. A
 * `#` inside a quoted argument or a bracket argument, or escaped by a backslash, starts no comment. A bracket opens
 * an argument only where an argument starts, not inside an unquoted one.
 */
internal object CMakeComments {
    fun blank(text: String): String {
        val result = StringBuilder(text)
        var index = 0
        var insideUnquotedArgument = false
        while (index < text.length) {
            val opener = if (insideUnquotedArgument) 0 else bracketOpenerLength(text, index)
            insideUnquotedArgument = continuesUnquotedArgument(text[index], opener)
            index = when {
                text[index] == '\\' -> index + 2
                text[index] == '"' -> afterQuoted(text, index)
                opener > 0 -> afterBracket(text, index, opener)
                text[index] == '#' -> blankComment(text, result, index)
                else -> index + 1
            }
        }
        return result.toString()
    }

    private fun continuesUnquotedArgument(character: Char, bracketOpener: Int): Boolean =
        character == '\\' || !(character.isWhitespace() || character in "()\"#" || bracketOpener > 0)

    private fun blankComment(text: String, result: StringBuilder, hash: Int): Int {
        val opener = bracketOpenerLength(text, hash + 1)
        val end = if (opener > 0) afterBracket(text, hash + 1, opener) else endOfLine(text, hash)
        for (index in hash until end) {
            if (text[index] != '\n') result.setCharAt(index, ' ')
        }
        return end
    }

    private fun afterQuoted(text: String, quote: Int): Int {
        var index = quote + 1
        while (index < text.length && text[index] != '"') index += if (text[index] == '\\') 2 else 1
        return minOf(index + 1, text.length)
    }

    fun afterBracket(text: String, bracket: Int, openerLength: Int): Int {
        val closer = bracketCloser(openerLength)
        val close = text.indexOf(closer, bracket + openerLength)
        return if (close < 0) text.length else close + closer.length
    }

    fun bracketCloser(openerLength: Int): String = "]" + "=".repeat(openerLength - 2) + "]"

    fun bracketOpenerLength(text: String, index: Int): Int {
        if (text.getOrNull(index) != '[') return 0
        var end = index + 1
        while (text.getOrNull(end) == '=') end++
        return if (text.getOrNull(end) == '[') end - index + 1 else 0
    }

    private fun endOfLine(text: String, from: Int): Int = text.indexOf('\n', from).takeIf { it >= 0 } ?: text.length
}
