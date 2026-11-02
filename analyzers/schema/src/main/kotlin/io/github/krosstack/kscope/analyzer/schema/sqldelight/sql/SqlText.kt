package io.github.krosstack.kscope.analyzer.schema.sqldelight.sql

/** A character of SQL code, outside comments, string literals and quoted identifiers, with its offset in the text. */
internal data class CodeCharacter(val offset: Int, val value: Char)

private val quotes = setOf('\'', '"', '`')

/** Scanning of SQL text that skips what is not code: comments, strings and quoted identifiers. */
internal object SqlText {
    /** [text] with comments replaced by spaces, so that offsets and lines stay what they were. */
    fun withoutComments(text: String): String {
        val builder = StringBuilder(text)
        var cursor = 0
        while (cursor < text.length) {
            val end = endOfOpaque(text, cursor)
            if (end != null && isComment(text, cursor)) blank(builder, cursor, end)
            cursor = end ?: (cursor + 1)
        }
        return builder.toString()
    }

    /** [text] with [transform] applied to each run of code between comments, strings and quoted identifiers. */
    fun mapCode(text: String, transform: (String) -> String): String {
        val builder = StringBuilder()
        var codeStart = 0
        var cursor = 0
        while (cursor < text.length) {
            val end = endOfOpaque(text, cursor)
            if (end != null) {
                builder.append(transform(text.substring(codeStart, cursor))).append(text, cursor, end)
                codeStart = end
            }
            cursor = end ?: (cursor + 1)
        }
        return builder.append(transform(text.substring(codeStart))).toString()
    }

    /** The code characters of [text] from [from] to [until], in order. */
    fun codeCharacters(text: String, from: Int = 0, until: Int = text.length): Sequence<CodeCharacter> = sequence {
        var cursor = from
        while (cursor < until) {
            val end = endOfOpaque(text, cursor)
            if (end == null) {
                yield(CodeCharacter(cursor, text[cursor]))
                cursor++
            } else {
                cursor = end
            }
        }
    }

    /** The offsets of the characters that are [separator] at nesting depth zero of parentheses. */
    fun topLevelSeparators(text: String, from: Int, until: Int, separator: Char): List<Int> {
        var depth = 0
        val found = mutableListOf<Int>()
        for ((offset, character) in codeCharacters(text, from, until)) {
            when {
                character == '(' -> depth++
                character == ')' -> depth--
                depth == 0 && character == separator -> found += offset
            }
        }
        return found
    }

    /** The offset of the parenthesis that closes the one at [open], or null if it is never closed. */
    fun closingParenthesis(text: String, open: Int): Int? {
        var depth = 0
        for ((offset, character) in codeCharacters(text, open)) {
            if (character == '(') depth++
            if (character == ')' && --depth == 0) return offset
        }
        return null
    }

    private fun isComment(text: String, at: Int) = text.startsWith("--", at) || text.startsWith("/*", at)

    private fun blank(builder: StringBuilder, from: Int, until: Int) {
        for (index in from until until) {
            if (builder[index] != '\n') builder.setCharAt(index, ' ')
        }
    }

    // The end of the comment, string or quoted identifier that starts at the offset, or null if none does.
    private fun endOfOpaque(text: String, at: Int): Int? = when {
        text.startsWith("--", at) -> endAt(text, text.indexOf('\n', at), 0)
        text.startsWith("/*", at) -> endAt(text, text.indexOf("*/", at + 2), 2)
        text[at] in quotes -> endOfQuoted(text, at, text[at])
        else -> null
    }

    // The end of a comment found at [index], or the end of the text when its closing mark is missing.
    private fun endAt(text: String, index: Int, mark: Int): Int = if (index < 0) text.length else index + mark

    // A quote doubled inside quoted text stands for the quote itself, so quoted text ends at an undoubled quote.
    private fun endOfQuoted(text: String, open: Int, quote: Char): Int {
        var cursor = open + 1
        while (cursor < text.length) {
            val isQuote = text[cursor] == quote
            val isDoubled = isQuote && cursor + 1 < text.length && text[cursor + 1] == quote
            if (isQuote && !isDoubled) return cursor + 1
            cursor += if (isDoubled) 2 else 1
        }
        return text.length
    }
}
