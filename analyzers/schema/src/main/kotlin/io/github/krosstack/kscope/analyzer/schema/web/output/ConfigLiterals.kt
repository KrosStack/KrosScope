package io.github.krosstack.kscope.analyzer.schema.web.output

import io.github.krosstack.kscope.analyzer.schema.lexing.SourceToken
import io.github.krosstack.kscope.analyzer.schema.lexing.TokenKind

private const val QUOTES = "\"'"
private const val BACKTICK = '`'
private const val TEMPLATE_OPENING = "\${"
private val pathCall = Regex("""^(?:path \. )?(?:resolve|join) \( __dirname((?: , (?:"[^"\\]*"|'[^'\\]*'))+) \)$""")
private val quoted = Regex(""""([^"\\]*)"|'([^'\\]*)'""")

/**
 * The values of a config that are read: string literals, and paths made from `__dirname` and string literals, which
 * are relative to the directory of the config.
 */
internal object ConfigLiterals {
    fun stringOf(token: SourceToken): String? {
        val text = token.text
        val quote = text.firstOrNull()
        val isString = token.kind == TokenKind.STRING && quote != null && quote in QUOTES
        val isTemplate = token.kind == TokenKind.IDENTIFIER && quote == BACKTICK && TEMPLATE_OPENING !in text
        val inner = text.drop(1).dropLast(1)
        val isClosed = text.length >= 2 && text.last() == quote
        return inner.takeIf { (isString || isTemplate) && isClosed && '\\' !in inner }
    }

    fun pathOf(value: List<SourceToken>): ConfigSetting.Literal? =
        value.singleOrNull()?.let(::stringOf)?.let { ConfigSetting.Literal(it) } ?: pathCallOf(value)

    private fun pathCallOf(value: List<SourceToken>): ConfigSetting.Literal? {
        val call = pathCall.matchEntire(value.joinToString(" ") { it.text }) ?: return null
        val path = quoted.findAll(call.groupValues[1]).joinToString("/") { it.groupValues[1] + it.groupValues[2] }
        return ConfigSetting.Literal(path, isConfigRelative = true)
    }
}
