package io.github.krosstack.kscope.analyzer.schema.web.output

import io.github.krosstack.kscope.analyzer.schema.lexing.SourceToken
import io.github.krosstack.kscope.analyzer.schema.lexing.TokenKind
import io.github.krosstack.kscope.analyzer.schema.lexing.afterBalanced

private val headedKeywords = setOf("if", "for", "while", "switch", "catch", "with")
private val bareKeywords = setOf("else", "try", "finally", "do")
private val statementEnds = setOf(";", "}")

/**
 * The `return` statements that end the function whose body is a range of tokens. A return inside a block of control
 * flow counts; a return inside an inner function, a class or an object does not.
 */
internal class FactoryReturns(private val tokens: List<SourceToken>) {
    fun inside(body: IntRange): List<Int> {
        val found = mutableListOf<Int>()
        var index = body.first
        while (index <= body.last) {
            if (tokens[index].isIdentifier("return")) found += index
            index = after(index, body)
        }
        return found
    }

    private fun after(index: Int, body: IntRange): Int {
        val token = tokens[index]
        return when {
            isHeaded(index) -> intoBlock(tokens.afterBalanced(index + 1, "(", ")"))
            token.kind == TokenKind.IDENTIFIER && token.text in bareKeywords -> intoBlock(index + 1)
            token.isPunctuation("(") -> tokens.afterBalanced(index, "(", ")")
            token.isPunctuation("[") -> tokens.afterBalanced(index, "[", "]")
            token.isPunctuation("{") -> braceEnd(index, body)
            else -> index + 1
        }
    }

    private fun braceEnd(open: Int, body: IntRange): Int =
        if (isStatement(open, body)) open + 1 else tokens.afterBalanced(open, "{", "}")

    private fun isHeaded(index: Int): Boolean {
        val token = tokens[index]
        val opensHeader = tokens.hasPunctuationAt(index + 1, "(")
        return token.kind == TokenKind.IDENTIFIER && token.text in headedKeywords && opensHeader
    }

    private fun isStatement(open: Int, body: IntRange): Boolean =
        open == body.first || tokens[open - 1].text in statementEnds

    private fun intoBlock(index: Int): Int = if (tokens.hasPunctuationAt(index, "{")) index + 1 else index
}
