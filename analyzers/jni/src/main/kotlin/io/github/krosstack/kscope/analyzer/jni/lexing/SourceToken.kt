package io.github.krosstack.kscope.analyzer.jni.lexing

import io.github.krosstack.kscope.model.TextRange

internal enum class TokenKind {
    IDENTIFIER,
    NUMBER,
    STRING,
    PUNCTUATION,
}

/** [start] and [end] are offsets in the text; the end offset is exclusive. */
internal data class SourceToken(
    val kind: TokenKind,
    val text: String,
    val range: TextRange,
    val start: Int,
    val end: Int,
) {
    fun isPunctuation(symbol: String): Boolean = kind == TokenKind.PUNCTUATION && text == symbol

    fun isIdentifier(name: String): Boolean = kind == TokenKind.IDENTIFIER && text == name
}
