package io.github.krosstack.kscope.analyzer.schema.document

private const val QUOTES = "\"'"
private const val TOKEN_BOUNDARIES = " \t[{,:"
private val whiteSpace = charArrayOf(' ', '\t')
private val blockHeader = Regex("[|>][+\\-0-9]*(\\s+#.*)?")

/** Whether a comment starts at [index]: a `#` after white space. */
internal fun isCommentAt(text: String, index: Int): Boolean =
    text[index] == '#' && index > 0 && text[index - 1].isWhitespace()

/** [text] without a trailing comment, which starts with `#` after white space outside quotes. */
internal fun withoutComment(text: String): String {
    var quote = ' '
    var index = 0
    while (index < text.length) {
        val character = text[index]
        when {
            quote != ' ' -> {
                if (quote == '"' && character == '\\') {
                    index++
                } else if (character == quote) {
                    quote = ' '
                }
            }

            character in QUOTES && isTokenStart(text, index) -> quote = character

            isCommentAt(text, index) -> return text.substring(0, index).trimEnd()
        }
        index++
    }
    return text
}

private fun isTokenStart(text: String, index: Int): Boolean = index == 0 || text[index - 1] in TOKEN_BOUNDARIES

/** The column of the value in [text] from [column], past anchors and tags: the length of [text] if there is none. */
internal fun valueColumn(text: String, column: Int): Int {
    var at = column
    while (at < text.length && (text[at].isWhitespace() || isProperty(text[at]))) {
        at = if (text[at].isWhitespace()) at + 1 else endOfToken(text, at)
    }
    return if (at >= text.length || isCommentAt(text, at)) text.length else at
}

private fun isProperty(character: Char): Boolean = character == '&' || character == '!'

private fun endOfToken(text: String, from: Int): Int {
    val end = text.indexOfAny(whiteSpace, from)
    return if (end < 0) text.length else end
}

/** Whether the text from [at] is the header of a block scalar: `|` or `>`, indentation and chomping, a comment. */
internal fun isBlockScalarHeader(text: String, at: Int): Boolean = blockHeader.matches(text.substring(at))
