package io.github.krosstack.kscope.analyzer.schema.document

private const val QUOTES = "\"'"
private const val NOT_A_KEY = "[{#|>*%@`"

/**
 * The key that starts a mapping line: its [name], where the name lies in the line from [start] to [end], and the
 * column [valueColumn] where the text after the colon begins.
 */
internal class YamlKey(val name: String, val start: Int, val end: Int, val valueColumn: Int)

internal fun isSequenceItem(text: String): Boolean = text == "-" || text.startsWith("- ") || text.startsWith("-\t")

/** The key of a line such as `name: value` or `"200":`; null for a line that is not a mapping entry. */
internal fun keyOf(text: String): YamlKey? {
    val first = text.firstOrNull() ?: return null
    return when {
        isSequenceItem(text) || first in NOT_A_KEY || text.startsWith("? ") -> null
        first in QUOTES -> quotedKey(text)
        else -> plainKey(text)
    }
}

private fun plainKey(text: String): YamlKey? {
    val colon = text.indices.firstOrNull { isSeparatorAt(text, it) || isCommentAt(text, it) }
        ?.takeIf { text[it] == ':' }
    return colon?.takeIf { it > 0 }?.let { YamlKey(text.substring(0, it).trimEnd(), 0, it, it + 1) }
}

private fun quotedKey(text: String): YamlKey? {
    val close = closingQuote(text, 0)
    val colon = (close + 1 until text.length).firstOrNull { !text[it].isWhitespace() }.takeIf { close >= 0 }
    return colon?.takeIf { isSeparatorAt(text, it) }
        ?.let { YamlKey(unquote(text.substring(1, close), text[0]), 1, close, it + 1) }
}

private fun isSeparatorAt(text: String, index: Int): Boolean =
    text[index] == ':' && (index + 1 == text.length || text[index + 1].isWhitespace())
