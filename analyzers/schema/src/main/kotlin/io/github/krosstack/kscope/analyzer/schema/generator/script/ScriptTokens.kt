package io.github.krosstack.kscope.analyzer.schema.generator.script

import io.github.krosstack.kscope.analyzer.schema.Span
import io.github.krosstack.kscope.analyzer.schema.lexing.SourceToken
import io.github.krosstack.kscope.analyzer.schema.lexing.TokenKind

private val statementEnds = setOf(";", "}", ")", ",")
private val openers = setOf("(", "{", "[")
private val closers = setOf(")", "}", "]")

private fun depthChange(token: SourceToken): Int = when {
    token.kind != TokenKind.PUNCTUATION -> 0
    token.text in openers -> 1
    token.text in closers -> -1
    else -> 0
}

/** The tokens of a build script and the questions the readers of its blocks and calls ask of them. */
internal class ScriptTokens(val tokens: List<SourceToken>) {
    fun isPunctuationAt(index: Int, symbol: String): Boolean = tokens.getOrNull(index)?.isPunctuation(symbol) == true

    fun isQualified(index: Int): Boolean = isPunctuationAt(index - 1, ".")

    /** Whether the expression that a literal at the token before [index] ends there, rather than goes on. */
    fun endsExpressionAt(index: Int): Boolean {
        val next = tokens.getOrNull(index)
        return next == null || next.kind != TokenKind.PUNCTUATION || next.text in statementEnds
    }

    /** The index of the token that closes the bracket opened at [open]; the number of tokens when it never closes. */
    fun closeOf(open: Int, opening: String, closing: String): Int {
        var depth = 0
        for (index in open until tokens.size) {
            if (tokens[index].isPunctuation(opening)) depth++
            if (tokens[index].isPunctuation(closing)) depth--
            if (depth == 0) return index
        }
        return tokens.size
    }

    fun blockAt(open: Int): ScriptBlock? {
        if (!isPunctuationAt(open, "{")) return null
        val close = closeOf(open, "{", "}")
        return ScriptBlock((open + 1) until close, minOf(close + 1, tokens.size))
    }

    fun blocksNamed(name: String): List<ScriptBlock> =
        tokens.indices.filter { tokens[it].isIdentifier(name) }.mapNotNull { blockAt(it + 1) }

    fun skipPast(index: Int): Int = when {
        isPunctuationAt(index, "{") -> closeOf(index, "{", "}") + 1
        isPunctuationAt(index, "(") -> closeOf(index, "(", ")") + 1
        isPunctuationAt(index, "[") -> closeOf(index, "[", "]") + 1
        else -> index + 1
    }

    /** The arguments in the tokens from [from] until [to], each a string literal or null. */
    fun argumentsBetween(from: Int, to: Int): List<SourceToken?> = argumentGroupsBetween(from, to).map { group ->
        group.singleOrNull()?.takeIf { it.kind == TokenKind.STRING }
    }

    fun argumentGroupsBetween(from: Int, to: Int): List<List<SourceToken>> {
        val groups = mutableListOf(mutableListOf<SourceToken>())
        var depth = 0
        for (token in tokens.subList(from, maxOf(from, to))) {
            depth += depthChange(token)
            val isSeparator = depth == 0 && token.isPunctuation(",")
            if (isSeparator) groups += mutableListOf<SourceToken>() else groups.last() += token
        }
        return groups.filter { it.isNotEmpty() }
    }

    fun span(from: Int, last: Int): Span = Span(tokens[from].span.start, tokens[minOf(last, tokens.size - 1)].span.end)
}
