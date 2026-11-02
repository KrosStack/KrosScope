package io.github.krosstack.kscope.analyzer.jni.loads

import io.github.krosstack.kscope.analyzer.jni.lexing.SourceToken
import io.github.krosstack.kscope.analyzer.jni.lexing.TokenKind
import io.github.krosstack.kscope.analyzer.jni.lexing.afterBalanced
import io.github.krosstack.kscope.model.TextRange

private const val WINDOW = 6
private const val ARGUMENT = 4
private const val AFTER_ARGUMENT = 5
private const val TRIPLE_QUOTE = "\"\"\""
private val hint = Regex("\\bload(Library)?\\b")
private val templateReference = Regex("[$][A-Za-z_{]")
private val escapedCharacter = Regex("\\\\([\\\\\"'])")

/**
 * One way to load a library: `System.loadLibrary("name")`, and what may follow its string argument. Kotlin also allows
 * a trailing comma before the closing parenthesis.
 */
private class LoadForm(
    val owner: String,
    val method: String,
    val terminators: Set<String>,
    val isPath: Boolean = false,
) {
    val call = "$owner.$method"

    fun matches(window: List<SourceToken>, dialect: JvmDialect): Boolean {
        if (window.size < WINDOW) return false
        val isCall = window[0].isIdentifier(owner) && window[1].isPunctuation(".") && window[2].isIdentifier(method)
        val isArgument = window[3].isPunctuation("(") && window[ARGUMENT].kind == TokenKind.STRING
        val isEnd = terminators.any(window[AFTER_ARGUMENT]::isPunctuation) || hasTrailingComma(window, dialect)
        return isCall && isArgument && isEnd
    }

    private fun hasTrailingComma(window: List<SourceToken>, dialect: JvmDialect): Boolean =
        dialect == JvmDialect.KOTLIN && window.size > WINDOW && window[AFTER_ARGUMENT].isPunctuation(",") &&
            window[WINDOW].isPunctuation(")")
}

private val forms = listOf(
    LoadForm("System", "loadLibrary", setOf(")")),
    LoadForm("System", "load", setOf(")"), isPath = true),
    LoadForm("Native", "load", setOf(",", ")")),
    LoadForm("Native", "loadLibrary", setOf(",")),
)

/**
 * Finds the calls that load a native library by a string literal: `System.loadLibrary("name")`,
 * `System.load("/path/libname.so")`, whose library is the file name without `lib` and the extension, and the JNA calls
 * `Native.load("name", ...)` and `Native.loadLibrary("name", ...)`. A name that is built, by a template or a
 * concatenation, is not a literal and is skipped, and so is anything in a comment or a string, because the tokens come
 * from [JvmTokenizer]. This is a lexical match, not a resolver: a method of another class with these names would match.
 */
internal class LoadCallScanner(private val text: String, private val dialect: JvmDialect) {
    fun scan(): List<LoadCall> {
        if (!hint.containsMatchIn(text)) return emptyList()
        val tokens = JvmTokenizer(text, dialect).tokens()
        return tokens.indices.mapNotNull { callAt(tokens, it) }
    }

    private fun callAt(tokens: List<SourceToken>, index: Int): LoadCall? {
        val window = tokens.subList(index, minOf(index + WINDOW + 1, tokens.size))
        val form = forms.firstOrNull { it.matches(window, dialect) }
        val argument = form?.let { valueOf(window[ARGUMENT].text) }
        if (form == null || argument.isNullOrBlank()) return null
        val close = tokens[tokens.afterBalanced(index + 3, "(", ")") - 1].range
        val start = window.first().range
        return LoadCall(
            library = if (form.isPath) libraryOfFile(argument) else argument,
            argument = argument,
            call = form.call,
            argumentRange = window[ARGUMENT].range,
            declaration = TextRange(start.startLine, start.startColumn, close.endLine, close.endColumn),
        )
    }

    private fun valueOf(literal: String): String? {
        val quotes = if (literal.startsWith(TRIPLE_QUOTE)) TRIPLE_QUOTE.length else 1
        val isClosed = literal.startsWith("\"") && literal.length >= 2 * quotes && literal.endsWith("\"".repeat(quotes))
        val body = literal.substring(quotes, maxOf(quotes, literal.length - quotes))
        val isBuilt = dialect.hasTemplates && templateReference.containsMatchIn(body)
        val isRaw = quotes > 1 && !dialect.escapesInTextBlocks
        return when {
            !isClosed || isBuilt -> null
            isRaw -> body
            else -> body.replace(escapedCharacter, "$1")
        }
    }
}
