package io.github.krosstack.kscope.analyzer.schema.web.output

import io.github.krosstack.kscope.analyzer.schema.lexing.SourceToken
import io.github.krosstack.kscope.analyzer.schema.lexing.TokenKind

/** How the properties of an object literal are written in the [tokens] of a config. */
internal class PropertySyntax(private val tokens: List<SourceToken>) {
    fun isKey(index: Int, key: String): Boolean {
        val token = tokens[index]
        return (token.kind == TokenKind.IDENTIFIER && token.text == key) || ConfigLiterals.stringOf(token) == key
    }

    fun hasColonAfter(index: Int, last: Int): Boolean = index < last && tokens[index + 1].isPunctuation(":")

    fun isSpreadAt(index: Int, scope: IntRange): Boolean =
        startsProperty(index, scope) && (0..2).all { tokens.getOrNull(index + it)?.isPunctuation(".") == true }

    fun isShorthandAt(index: Int, scope: IntRange): Boolean =
        startsProperty(index, scope) && (index == scope.last || tokens[index + 1].isPunctuation(","))

    private fun startsProperty(index: Int, scope: IntRange): Boolean =
        index == scope.first || tokens[index - 1].isPunctuation(",")
}
