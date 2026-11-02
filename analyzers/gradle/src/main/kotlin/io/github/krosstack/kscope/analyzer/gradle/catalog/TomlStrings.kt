package io.github.krosstack.kscope.analyzer.gradle.catalog

/** Finds the end of a TOML string, which can be quoted with one or three quotes of either kind. */
internal class TomlStrings(private val text: String) {
    fun after(open: Int): Int {
        val quote = text[open]
        val triple = "$quote$quote$quote"
        return if (text.startsWith(triple, open)) afterMultiline(open, triple) else afterSingleLine(open, quote)
    }

    private fun afterMultiline(open: Int, delimiter: String): Int {
        val close = text.indexOf(delimiter, open + delimiter.length)
        return if (close < 0) text.length else close + delimiter.length
    }

    private fun afterSingleLine(open: Int, quote: Char): Int {
        var index = open + 1
        while (index < text.length && text[index] != quote && text[index] != '\n') {
            index += if (text[index] == '\\' && quote == '"') 2 else 1
        }
        return index + 1
    }
}
