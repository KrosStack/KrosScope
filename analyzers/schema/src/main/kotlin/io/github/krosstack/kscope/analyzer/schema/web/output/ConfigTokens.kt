package io.github.krosstack.kscope.analyzer.schema.web.output

import io.github.krosstack.kscope.analyzer.schema.lexing.KotlinTokenizer
import io.github.krosstack.kscope.analyzer.schema.lexing.SourceToken
import io.github.krosstack.kscope.analyzer.schema.lexing.TokenKind
import io.github.krosstack.kscope.analyzer.schema.lexing.afterBalanced

private val openings = setOf("{", "[", "(")
private val closings = setOf("}", "]", ")")

/**
 * The properties of a bundler config, found lexically. The tokenizer splits JavaScript and TypeScript as it splits
 * Kotlin as far as comments, quotes and punctuation go, which is all that is read here: nothing in a comment or a
 * string is taken for code. A property is a key followed by a colon; a key written alone in an object is a shorthand
 * for a variable, so its value is computed, and so is the value that a spread in an object may set. The last
 * assignment of a key in an object decides. The top-level properties are those of the config object: the object that
 * `defineConfig` receives or that is exported, or that the function there returns. A config whose object cannot be
 * told has computed properties.
 */
internal class ConfigTokens(text: String) {
    private val tokens = KotlinTokenizer(text).tokens()
    private val syntax = PropertySyntax(tokens)

    fun first(key: String): ConfigSetting {
        val index = tokens.indices.firstOrNull { syntax.isKey(it, key) && syntax.hasColonAfter(it, tokens.lastIndex) }
        return index?.let { classify(valueRange(it + 2, tokens.lastIndex)) } ?: ConfigSetting.Absent
    }

    fun within(block: ConfigSetting.Block, key: String): ConfigSetting {
        var depth = 0
        var setting: ConfigSetting = ConfigSetting.Absent
        for (index in block.interior) {
            if (depth == 0) setting = propertyAt(index, key, block.interior) ?: setting
            depth += depthChange(tokens[index])
        }
        return setting
    }

    fun topLevel(key: String): ConfigSetting = ofConfigObject(key) { ConfigSetting.Absent }

    fun ofConfig(key: String): ConfigSetting = ofConfigObject(key) { first(key) }

    private fun ofConfigObject(key: String, whenMissing: () -> ConfigSetting): ConfigSetting =
        when (val lookup = ConfigObjectLocator(tokens).lookup()) {
            is ConfigObjectLookup.Found -> within(blockAt(lookup.opening), key)
            ConfigObjectLookup.Missing -> whenMissing()
            ConfigObjectLookup.Unidentified -> ConfigSetting.Computed
        }

    private fun propertyAt(index: Int, key: String, scope: IntRange): ConfigSetting? = when {
        syntax.isSpreadAt(index, scope) -> ConfigSetting.Computed
        !syntax.isKey(index, key) -> null
        syntax.hasColonAfter(index, scope.last) -> classify(valueRange(index + 2, scope.last))
        syntax.isShorthandAt(index, scope) -> ConfigSetting.Computed
        else -> null
    }

    private fun valueRange(start: Int, last: Int): IntRange {
        var depth = 0
        var end = start
        while (end <= last) {
            val token = tokens[end]
            if (depth == 0 && (token.isPunctuation(",") || depthChange(token) < 0)) break
            depth += depthChange(token)
            end++
        }
        return start until end
    }

    private fun classify(value: IntRange): ConfigSetting {
        val first = tokens.getOrNull(value.first)?.takeIf { !value.isEmpty() }
        val isObjectInArray = tokens.getOrNull(value.first + 1)?.isPunctuation("{") == true
        return when {
            first == null -> ConfigSetting.Computed
            first.isPunctuation("{") -> blockAt(value.first)
            first.isPunctuation("[") -> if (isObjectInArray) blockAt(value.first + 1) else ConfigSetting.Computed
            else -> ConfigLiterals.pathOf(value.map { tokens[it] }) ?: ConfigSetting.Computed
        }
    }

    private fun blockAt(open: Int): ConfigSetting.Block {
        val after = tokens.afterBalanced(open, "{", "}")
        return ConfigSetting.Block((open + 1) until maxOf(after - 1, open + 1))
    }

    private fun depthChange(token: SourceToken): Int = when {
        token.kind != TokenKind.PUNCTUATION -> 0
        token.text in openings -> 1
        token.text in closings -> -1
        else -> 0
    }
}
