package io.github.krosstack.kscope.analyzer.schema.generator.script

import io.github.krosstack.kscope.analyzer.schema.lexing.SourceToken
import io.github.krosstack.kscope.analyzer.schema.lexing.TokenKind

/**
 * Finds the calls of a build script that a block follows, wherever in the script they are made, with or without a
 * receiver and type arguments: `tasks.named("x") { }`, `tasks.register<Copy>("x") { }`, `project.copy { }` and the
 * Groovy `task x(type: Copy) { }`. [ScriptCalls] passes these over, because it reads the calls at the top of a range
 * and only those that no dot qualifies. A call without a block is not found. Calls come in the order of the script:
 * those with one of the given names, or those of the identifiers that follow a keyword, as in Groovy's
 * `task x(type: Copy) { }`.
 */
internal class QualifiedCalls(private val script: ScriptTokens) {
    private val tokens = script.tokens

    fun named(names: Set<String>): List<QualifiedCall> =
        tokens.indices.filter { tokens[it].isName() && tokens[it].text in names }.mapNotNull(::callAt)

    fun declaredBy(keyword: String): List<QualifiedCall> =
        tokens.indices.filter { tokens[it].isIdentifier(keyword) && tokens.getOrNull(it + 1).isName() }
            .mapNotNull { callAt(it + 1) }

    private fun callAt(nameIndex: Int): QualifiedCall? {
        val open = afterTypeArguments(nameIndex + 1)
        val close = if (script.isPunctuationAt(open, "(")) script.closeOf(open, "(", ")") else open - 1
        return script.blockAt(close + 1)?.let { block ->
            QualifiedCall(
                name = tokens[nameIndex],
                receiver = receiverOf(nameIndex),
                typeArguments = if (open > nameIndex + 1) tokens.subList(nameIndex + 2, open - 1) else emptyList(),
                arguments = if (close >= open) script.argumentGroupsBetween(open + 1, close) else emptyList(),
                block = block,
            )
        }
    }

    private fun SourceToken?.isName(): Boolean = this?.kind == TokenKind.IDENTIFIER

    private fun afterTypeArguments(index: Int): Int =
        if (script.isPunctuationAt(index, "<")) minOf(script.closeOf(index, "<", ">") + 1, tokens.size) else index

    private fun receiverOf(nameIndex: Int): String? =
        if (script.isQualified(nameIndex)) tokens.getOrNull(nameIndex - 2)?.text.orEmpty() else null
}
