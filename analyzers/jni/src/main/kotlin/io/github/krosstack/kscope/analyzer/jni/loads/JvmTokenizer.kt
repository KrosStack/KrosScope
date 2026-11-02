package io.github.krosstack.kscope.analyzer.jni.loads

import io.github.krosstack.kscope.analyzer.jni.lexing.SourceToken
import io.github.krosstack.kscope.analyzer.jni.lexing.TextCursor
import io.github.krosstack.kscope.analyzer.jni.lexing.TokenKind
import io.github.krosstack.kscope.model.TextRange

private const val TRIPLE_QUOTE = "\"\"\""
private const val TEMPLATE_OPENING = "\${"

private fun isLineBreak(character: Char) = character == '\n' || character == '\r'

private fun isIdentifierStart(character: Char) = character.isLetter() || character == '_' || character == '$'

private fun isIdentifierPart(character: Char) = isIdentifierStart(character) || character.isDigit()

/** Skips what is not code: white space and comments, which nest in Kotlin and do not in Java. */
private class JvmTrivia(private val source: TextCursor, private val dialect: JvmDialect) {
    fun skip() {
        while (!source.isAtEnd) {
            when {
                source.startsWith("//") -> skipLineComment()
                source.startsWith("/*") -> skipBlockComment()
                source.text[source.offset].isWhitespace() -> source.advance()
                else -> return
            }
        }
    }

    private fun skipLineComment() {
        while (!source.isAtEnd && !isLineBreak(source.text[source.offset])) source.advance()
    }

    private fun skipBlockComment() {
        var depth = 0
        while (!source.isAtEnd) {
            val step = when {
                source.startsWith("/*") && (dialect.nestsComments || depth == 0) -> 1
                source.startsWith("*/") -> -1
                else -> 0
            }
            depth += step
            source.advanceTo(source.offset + if (step == 0) 1 else 2)
            if (depth == 0) return
        }
    }
}

/** Reads the literals that hide their content from the code around them, with the templates of Kotlin in them. */
private class JvmLiterals(
    private val source: TextCursor,
    private val dialect: JvmDialect,
    private val trivia: JvmTrivia,
) {
    fun string() {
        if (source.startsWith(TRIPLE_QUOTE)) multiLine() else singleLine()
    }

    fun character() {
        val text = source.text
        source.advance()
        while (!source.isAtEnd && text[source.offset] != '\'' && !isLineBreak(text[source.offset])) {
            if (text[source.offset] == '\\') source.advance()
            if (!source.isAtEnd) source.advance()
        }
        if (!source.isAtEnd && text[source.offset] == '\'') source.advance()
    }

    private fun singleLine() {
        val text = source.text
        source.advance()
        while (!source.isAtEnd && text[source.offset] != '"' && !isLineBreak(text[source.offset])) {
            content(escapes = true)
        }
        if (!source.isAtEnd && text[source.offset] == '"') source.advance()
    }

    private fun multiLine() {
        source.advanceTo(source.offset + TRIPLE_QUOTE.length)
        while (!source.isAtEnd && !source.startsWith(TRIPLE_QUOTE)) content(dialect.escapesInTextBlocks)
        while (!source.isAtEnd && source.text[source.offset] == '"') source.advance()
    }

    private fun content(escapes: Boolean) {
        val text = source.text
        when {
            escapes && text[source.offset] == '\\' -> source.advanceTo(minOf(source.offset + 2, text.length))
            dialect.hasTemplates && source.startsWith(TEMPLATE_OPENING) -> template()
            else -> source.advance()
        }
    }

    private fun template() {
        source.advanceTo(source.offset + TEMPLATE_OPENING.length)
        var depth = 1
        while (depth > 0) {
            trivia.skip()
            if (source.isAtEnd) return
            depth += expressionPart()
        }
    }

    private fun expressionPart(): Int {
        val current = source.text[source.offset]
        when (current) {
            '"' -> string()
            '\'' -> character()
            else -> source.advance()
        }
        return when (current) {
            '{' -> 1
            '}' -> -1
            else -> 0
        }
    }
}

/**
 * Splits Kotlin or Java text into identifiers, numbers, string and character literals, and single-character
 * punctuation. Comments and white space are dropped, and the content of a literal is one token, whether it is a
 * string, a raw string, a text block or a character: nothing in a comment or a literal is taken for code. The
 * `${...}` of a Kotlin template is part of the string around it, whatever strings it holds. Unterminated comments and
 * literals end at the end of the file or of the line; the tokenizer never fails. Lines and columns start at 1.
 */
internal class JvmTokenizer(text: String, private val dialect: JvmDialect) {
    private val source = TextCursor(text)
    private val trivia = JvmTrivia(source, dialect)
    private val literals = JvmLiterals(source, dialect, trivia)

    fun tokens(): List<SourceToken> {
        val tokens = mutableListOf<SourceToken>()
        while (true) {
            trivia.skip()
            if (source.isAtEnd) return tokens
            tokens += token()
        }
    }

    private fun token(): SourceToken {
        val start = source.offset
        val startLine = source.line
        val startColumn = source.column
        val kind = readKind()
        val range = TextRange(startLine, startColumn, source.line, source.column)
        return SourceToken(kind, source.text.substring(start, source.offset), range, start, source.offset)
    }

    private fun readKind(): TokenKind {
        val character = source.text[source.offset]
        return when {
            isIdentifierStart(character) -> identifier()
            character == '`' && dialect == JvmDialect.KOTLIN -> quotedIdentifier()
            character.isDigit() -> number()
            character == '"' || character == '\'' -> literal(character)
            else -> punctuation()
        }
    }

    private fun literal(quote: Char): TokenKind {
        if (quote == '"') literals.string() else literals.character()
        return TokenKind.STRING
    }

    private fun punctuation(): TokenKind {
        source.advance()
        return TokenKind.PUNCTUATION
    }

    private fun identifier(): TokenKind {
        advanceWhile(::isIdentifierPart)
        return TokenKind.IDENTIFIER
    }

    private fun number(): TokenKind {
        advanceWhile { isIdentifierPart(it) || isFraction() }
        return TokenKind.NUMBER
    }

    private fun quotedIdentifier(): TokenKind {
        source.advance()
        advanceWhile { it != '`' && !isLineBreak(it) }
        if (!source.isAtEnd && source.text[source.offset] == '`') source.advance()
        return TokenKind.IDENTIFIER
    }

    private fun isFraction() =
        source.text[source.offset] == '.' && source.text.getOrNull(source.offset + 1)?.isDigit() == true

    private fun advanceWhile(test: (Char) -> Boolean) {
        while (!source.isAtEnd && test(source.text[source.offset])) source.advance()
    }
}
