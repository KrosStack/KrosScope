package io.github.krosstack.kscope.analyzer.schema.document

private const val HEX_DIGITS = 4
private const val HEX_RADIX = 16
private val lineFold = Regex("[ \t]*\r?\n[ \t]*")

/** The offset of the quote that closes the one at [open], or -1; a double quote has escapes, a single one doubles. */
internal fun closingQuote(text: String, open: Int): Int {
    val quote = text[open]
    var index = open + 1
    while (index < text.length) {
        val character = text[index]
        val isEscape = quote == '"' && character == '\\'
        val isDoubled = quote == '\'' && character == quote && text.getOrNull(index + 1) == quote
        if (!isEscape && !isDoubled && character == quote) return index
        index += if (isEscape || isDoubled) 2 else 1
    }
    return -1
}

/** The value that [content], the text between two [quote] characters, stands for; line breaks fold to a space. */
internal fun unquote(content: String, quote: Char): String {
    val folded = content.replace(lineFold, " ")
    return if (quote == '"') unescape(folded) else folded.replace("''", "'")
}

private fun unescape(content: String): String {
    val result = StringBuilder()
    var index = 0
    while (index < content.length) {
        val isEscape = content[index] == '\\' && index + 1 < content.length
        if (isEscape) {
            index += appendEscape(content, index + 1, result)
        } else {
            result.append(content[index++])
        }
    }
    return result.toString()
}

private fun appendEscape(content: String, at: Int, result: StringBuilder): Int {
    val code = content[at]
    val digits = content.substring(at + 1, minOf(at + 1 + HEX_DIGITS, content.length))
    val unicode = digits.takeIf { code == 'u' && it.length == HEX_DIGITS }?.toIntOrNull(HEX_RADIX)
    result.append(
        when {
            unicode != null -> unicode.toChar()
            code == 'n' -> '\n'
            code == 't' -> '\t'
            code == 'r' -> '\r'
            else -> code
        },
    )
    return if (unicode != null) HEX_DIGITS + 2 else 2
}
