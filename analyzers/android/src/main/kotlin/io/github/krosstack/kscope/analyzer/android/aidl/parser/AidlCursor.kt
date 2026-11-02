package io.github.krosstack.kscope.analyzer.android.aidl.parser

import io.github.krosstack.kscope.model.TextRange

/**
 * A position in the tokens of a file. The first thing that does not fit the grammar becomes the [problem]; from then
 * on the cursor stands at the end of the file, so that every loop of the parser ends and nothing more is read.
 */
internal class AidlCursor(val text: String, private val tokens: List<AidlToken>) {
    private val endToken = tokens.last()
    private var index = 0

    var problem: AidlProblem? = null
        private set

    /** The token consumed last. */
    var previous: AidlToken = endToken
        private set

    val current: AidlToken get() = if (problem != null) endToken else tokens[index]

    val isAtEnd: Boolean get() = current.kind == AidlTokenKind.END

    fun take(): AidlToken {
        val token = current
        if (token.kind != AidlTokenKind.END) {
            index++
            previous = token
        }
        return token
    }

    fun isSymbol(symbol: String): Boolean = current.kind == AidlTokenKind.SYMBOL && current.text == symbol

    fun isWord(word: String): Boolean = current.kind == AidlTokenKind.WORD && current.text == word

    fun acceptSymbol(symbol: String): Boolean = isSymbol(symbol).also { if (it) take() }

    fun expectSymbol(symbol: String): AidlToken {
        if (!isSymbol(symbol)) fail("Expected '$symbol'")
        return take()
    }

    /** The name at the cursor, or an empty name at the problem when the cursor stands at something else. */
    fun expectName(description: String): AidlName {
        if (current.kind != AidlTokenKind.WORD) fail("Expected $description")
        val token = take()
        return AidlName(token.text, token.range)
    }

    fun fail(expectation: String) {
        if (problem != null) return
        val found = if (isAtEnd) "the end of the file" else "'${current.text}'"
        problem = AidlProblem("$expectation but found $found", current.range)
    }

    /** The range from the start of [first] to the end of the token consumed last. */
    fun rangeFrom(first: AidlToken): TextRange =
        TextRange(first.range.startLine, first.range.startColumn, previous.range.endLine, previous.range.endColumn)

    /** Skips `@Name` and `@Name(...)`, any number of times. */
    fun skipAnnotations() {
        while (acceptSymbol("@")) {
            expectName("an annotation name")
            while (acceptSymbol(".")) expectName("an annotation name")
            if (isSymbol("(")) skipBalanced("(", ")")
        }
    }

    /** Skips from the opening symbol at the cursor to its closing one, nested pairs included. */
    fun skipBalanced(open: String, close: String) {
        var depth = 0
        do {
            if (isSymbol(open)) depth++
            if (isSymbol(close)) depth--
            take()
        } while (depth > 0 && !isAtEnd)
        if (depth > 0) fail("Expected '$close'")
    }
}

internal fun AidlCursor.acceptWord(word: String): Boolean = isWord(word).also { if (it) take() }
