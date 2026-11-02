package io.github.krosstack.kscope.analyzer.schema.lexing

import io.github.krosstack.kscope.analyzer.schema.Span

internal enum class TokenKind {
    IDENTIFIER,
    NUMBER,
    STRING,
    PUNCTUATION,
}

/** A token of source text with the [span] of offsets it covers. */
internal data class SourceToken(val kind: TokenKind, val text: String, val span: Span) {
    fun isPunctuation(symbol: String): Boolean = kind == TokenKind.PUNCTUATION && text == symbol

    fun isIdentifier(name: String): Boolean = kind == TokenKind.IDENTIFIER && text == name
}
