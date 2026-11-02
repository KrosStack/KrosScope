package io.github.krosstack.kscope.analyzer.schema.web.output

import io.github.krosstack.kscope.analyzer.schema.lexing.SourceToken
import io.github.krosstack.kscope.analyzer.schema.lexing.TokenKind
import io.github.krosstack.kscope.analyzer.schema.lexing.afterBalanced
import io.github.krosstack.kscope.analyzer.schema.web.output.ConfigObjectLookup.Found
import io.github.krosstack.kscope.analyzer.schema.web.output.ConfigObjectLookup.Missing
import io.github.krosstack.kscope.analyzer.schema.web.output.ConfigObjectLookup.Unidentified

private const val FUNCTION_HEAD = 2

/**
 * Finds the config object of a bundler config: the object literal that `defineConfig` receives, that `export default`
 * or `module.exports =` holds, or that a function or an arrow of these returns. An arrow with an expression body
 * yields its object; a function with a block body yields the object of its only `return`. A function with several
 * returns, or one that returns anything but an object literal, has no config object that can be told.
 */
internal class ConfigObjectLocator(private val tokens: List<SourceToken>) {
    fun lookup(): ConfigObjectLookup {
        val start = tokens.indices.firstNotNullOfOrNull(::expressionAfterAnchor)
        return if (start == null) Missing else expressionAt(start)
    }

    private fun expressionAfterAnchor(index: Int): Int? {
        val token = tokens[index]
        return when {
            token.isIdentifier("defineConfig") && tokens.hasPunctuationAt(index + 1, "(") -> index + 2
            token.isIdentifier("default") && tokens.hasIdentifierAt(index - 1, "export") -> index + 1
            token.isIdentifier("exports") && isExportsAssignmentAt(index) -> index + 2
            else -> null
        }
    }

    private fun expressionAt(index: Int): ConfigObjectLookup {
        val token = tokens.getOrNull(index) ?: return Unidentified
        return when {
            token.isPunctuation("{") -> Found(index)
            token.isPunctuation("[") -> if (tokens.hasPunctuationAt(index + 1, "{")) Found(index + 1) else Unidentified
            token.isIdentifier("defineConfig") && tokens.hasPunctuationAt(index + 1, "(") -> expressionAt(index + 2)
            token.isPunctuation("(") -> parenthesizedAt(index)
            else -> functionAt(index)
        }
    }

    private fun parenthesizedAt(open: Int): ConfigObjectLookup {
        val after = tokens.afterBalanced(open, "(", ")")
        return if (isArrowAt(after)) bodyAt(afterArrow(after)) else expressionAt(open + 1)
    }

    private fun functionAt(index: Int): ConfigObjectLookup {
        val token = tokens[index]
        return when {
            token.isIdentifier("async") -> expressionAt(index + 1)
            token.isIdentifier("function") -> functionBodyAt(index + 1)
            token.kind == TokenKind.IDENTIFIER && isArrowAt(index + 1) -> bodyAt(afterArrow(index + 1))
            else -> Unidentified
        }
    }

    private fun functionBodyAt(index: Int): ConfigObjectLookup {
        val parameters = (index..index + FUNCTION_HEAD).firstOrNull { tokens.hasPunctuationAt(it, "(") }
        val open = parameters ?: return Unidentified
        val body = tokens.afterBalanced(open, "(", ")")
        return if (tokens.hasPunctuationAt(body, "{")) returnedObjectIn(body) else Unidentified
    }

    private fun bodyAt(index: Int): ConfigObjectLookup =
        if (tokens.hasPunctuationAt(index, "{")) returnedObjectIn(index) else expressionAt(index)

    private fun returnedObjectIn(open: Int): ConfigObjectLookup {
        val after = tokens.afterBalanced(open, "{", "}")
        val returns = FactoryReturns(tokens).inside((open + 1) until maxOf(after - 1, open + 1))
        val returned = returns.singleOrNull()
        val isObject = returned != null && tokens.hasPunctuationAt(returned + 1, "{")
        return if (isObject) Found(returned + 1) else Unidentified
    }

    private fun afterArrow(arrow: Int): Int = arrow + 2

    private fun isArrowAt(index: Int): Boolean =
        tokens.hasPunctuationAt(index, "=") && tokens.hasPunctuationAt(index + 1, ">")

    private fun isExportsAssignmentAt(index: Int): Boolean {
        val isMember = tokens.hasPunctuationAt(index - 1, ".")
        val isAssigned = tokens.hasPunctuationAt(index + 1, "=") && !tokens.hasPunctuationAt(index + 2, "=")
        return isMember && isAssigned
    }
}
