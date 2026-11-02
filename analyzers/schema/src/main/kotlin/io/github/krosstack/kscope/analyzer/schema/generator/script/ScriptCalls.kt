package io.github.krosstack.kscope.analyzer.schema.generator.script

import io.github.krosstack.kscope.analyzer.schema.lexing.TokenKind

private const val SET_OPENING = 3
private const val VALUE_END = 3

/**
 * Finds the calls of a build script in Kotlin or in Groovy that are made at the top of a range of tokens, not in a
 * block or in the arguments of another call. A call has one of the forms `name(arguments)`, `name.set(arguments)`,
 * `name = value`, `name value` and `name { block }`, and may be followed by a block. Whatever else lies in the range
 * is passed over.
 */
internal class ScriptCalls(private val script: ScriptTokens) {
    private val tokens = script.tokens

    fun within(range: IntRange): List<ScriptCall> {
        val calls = mutableListOf<ScriptCall>()
        var index = range.first
        while (index <= range.last) {
            val call = callAt(index)
            if (call != null) calls += call
            index = call?.end ?: script.skipPast(index)
        }
        return calls
    }

    private fun callAt(index: Int): ScriptCall? = when {
        tokens[index].kind != TokenKind.IDENTIFIER || script.isQualified(index) -> null
        script.isPunctuationAt(index + 1, "(") -> invocation(index, index + 1)
        script.isPunctuationAt(index + 1, "{") -> blockCall(index)
        script.isPunctuationAt(index + 1, "=") -> assignment(index)
        script.isPunctuationAt(index + 1, ".") -> setter(index)
        tokens.getOrNull(index + 1)?.kind == TokenKind.STRING -> command(index)
        else -> null
    }

    private fun invocation(nameIndex: Int, open: Int): ScriptCall {
        val close = script.closeOf(open, "(", ")")
        val after = minOf(close + 1, tokens.size)
        val block = script.blockAt(after)
        return ScriptCall(
            name = tokens[nameIndex],
            arguments = script.argumentsBetween(open + 1, close),
            span = script.span(nameIndex, close),
            end = block?.end ?: after,
            block = block,
        )
    }

    private fun setter(index: Int): ScriptCall? {
        val opening = index + SET_OPENING
        val isSet = tokens.getOrNull(index + 2)?.isIdentifier("set") == true && script.isPunctuationAt(opening, "(")
        return if (isSet) invocation(index, opening) else null
    }

    private fun assignment(index: Int): ScriptCall? {
        val value = tokens.getOrNull(index + 2)
        val isLiteral = value?.kind == TokenKind.STRING && script.endsExpressionAt(index + VALUE_END)
        return if (value == null || value.isPunctuation("=")) {
            null
        } else {
            ScriptCall(
                name = tokens[index],
                arguments = listOf(value.takeIf { isLiteral }),
                span = script.span(index, index + 2),
                end = if (isLiteral) index + VALUE_END else index + 2,
            )
        }
    }

    private fun command(index: Int): ScriptCall {
        val isLiteral = script.endsExpressionAt(index + 2)
        return ScriptCall(
            name = tokens[index],
            arguments = listOf(tokens[index + 1].takeIf { isLiteral }),
            span = script.span(index, index + 1),
            end = index + 2,
        )
    }

    private fun blockCall(index: Int): ScriptCall? = script.blockAt(index + 1)?.let { block ->
        ScriptCall(tokens[index], emptyList(), tokens[index].span, block.end, block)
    }
}
