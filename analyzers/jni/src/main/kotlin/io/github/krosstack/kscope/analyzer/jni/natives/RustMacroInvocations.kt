package io.github.krosstack.kscope.analyzer.jni.natives

import io.github.krosstack.kscope.analyzer.jni.lexing.SourceToken
import io.github.krosstack.kscope.analyzer.jni.lexing.TokenKind
import io.github.krosstack.kscope.analyzer.jni.lexing.afterBalanced

private const val MACRO_RULES = "macro_rules"
private val closers = mapOf("(" to ")", "[" to "]", "{" to "}")

/**
 * Finds the macro invocations and `macro_rules!` definitions in Rust tokens, whose token tree, in any of the three
 * delimiters, is not code of the file: what is written inside is only the input of the macro.
 */
internal class RustMacroInvocations(private val tokens: List<SourceToken>) {
    /** The index after the token tree of the macro invocation that starts at [index]; null when none starts there. */
    fun endOfInvocationAt(index: Int): Int? {
        val open = openingOfInvocationAt(index) ?: return null
        val opening = tokens[open].text
        return closers[opening]?.let { tokens.afterBalanced(open, opening, it) }
    }

    private fun openingOfInvocationAt(index: Int): Int? {
        val isInvocation = tokens[index].kind == TokenKind.IDENTIFIER &&
            tokens.getOrNull(index + 1)?.isPunctuation("!") == true
        val open = if (tokens[index].text == MACRO_RULES) index + 3 else index + 2
        return open.takeIf { isInvocation && tokens.getOrNull(it)?.kind == TokenKind.PUNCTUATION }
    }
}
