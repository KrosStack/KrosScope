package io.github.krosstack.kscope.analyzer.schema.lexing

import io.github.krosstack.kscope.analyzer.schema.Span

private const val TRIPLE_QUOTE = "\"\"\""
private const val TRIPLE_APOSTROPHE = "'''"
private const val TEMPLATE_OPENING = "\${"

private fun isLineBreak(character: Char) = character == '\n' || character == '\r'

private fun isIdentifierStart(character: Char) = character.isLetter() || character == '_' || character == '$'

private fun isIdentifierPart(character: Char) = isIdentifierStart(character) || character.isDigit()

/** Skips what is not code: white space and comments, whose blocks nest in Kotlin. */
private class Trivia(private val source: SourceCursor) {
    fun skip() {
        while (!source.isAtEnd) {
            when {
                source.startsWith("//") -> skipLineComment()
                source.startsWith("/*") -> skipBlockComment()
                source.current.isWhitespace() -> source.advance()
                else -> return
            }
        }
    }

    private fun skipLineComment() {
        while (!source.isAtEnd && !isLineBreak(source.current)) source.advance()
    }

    private fun skipBlockComment() {
        var depth = 0
        while (!source.isAtEnd) {
            val step = when {
                source.startsWith("/*") -> 1
                source.startsWith("*/") -> -1
                else -> 0
            }
            depth += step
            source.advanceTo(source.offset + if (step == 0) 1 else 2)
            if (depth == 0) return
        }
    }
}

/** Reads the literals that hide their content from the code around them, with the templates in them. */
private class Literals(private val source: SourceCursor) {
    fun string() {
        if (source.startsWith(TRIPLE_QUOTE)) multiLine() else singleLine()
    }

    fun character() {
        if (source.startsWith(TRIPLE_APOSTROPHE)) apostropheBlock() else apostrophes()
    }

    private fun apostrophes() {
        source.advance()
        while (!source.isAtEnd && source.current != '\'' && !isLineBreak(source.current)) {
            if (source.current == '\\') source.advance()
            if (!source.isAtEnd) source.advance()
        }
        if (!source.isAtEnd && source.current == '\'') source.advance()
    }

    private fun apostropheBlock() {
        source.advanceTo(source.offset + TRIPLE_APOSTROPHE.length)
        while (!source.isAtEnd && !source.startsWith(TRIPLE_APOSTROPHE)) source.advance()
        source.advanceTo(source.offset + TRIPLE_APOSTROPHE.length)
    }

    private fun singleLine() {
        source.advance()
        while (!source.isAtEnd && source.current != '"' && !isLineBreak(source.current)) content(escapes = true)
        if (!source.isAtEnd && source.current == '"') source.advance()
    }

    private fun multiLine() {
        source.advanceTo(source.offset + TRIPLE_QUOTE.length)
        while (!source.isAtEnd && !source.startsWith(TRIPLE_QUOTE)) content(escapes = false)
        while (!source.isAtEnd && source.current == '"') source.advance()
    }

    private fun content(escapes: Boolean) {
        when {
            escapes && source.current == '\\' -> source.advanceTo(source.offset + 2)
            source.startsWith(TEMPLATE_OPENING) -> template()
            else -> source.advance()
        }
    }

    private fun template() {
        source.advanceTo(source.offset + TEMPLATE_OPENING.length)
        var depth = 1
        while (!source.isAtEnd && depth > 0) {
            val current = source.current
            when (current) {
                '"' -> string()
                '\'' -> character()
                else -> source.advance()
            }
            depth += when (current) {
                '{' -> 1
                '}' -> -1
                else -> 0
            }
        }
    }
}

/**
 * Splits Kotlin text into identifiers, numbers, string and character literals, and single-character punctuation.
 * Comments and white space are dropped, and the content of a literal is one token, whether it is a string, a raw string
 * or a character: nothing in a comment or a literal is taken for code. The `${...}` of a template is part of the string
 * around it, whatever strings it holds. Groovy reads as well: its single-quoted strings are tokens like characters,
 * and so are its blocks in triple apostrophes. Unterminated comments and literals end at the end of the file or of the
 * line; the tokenizer never fails.
 */
internal class KotlinTokenizer(text: String) {
    private val source = SourceCursor(text)
    private val trivia = Trivia(source)
    private val literals = Literals(source)

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
        val kind = readKind()
        return SourceToken(kind, source.text.substring(start, source.offset), Span(start, source.offset))
    }

    private fun readKind(): TokenKind {
        val character = source.current
        return when {
            isIdentifierStart(character) -> identifier()
            character == '`' -> quotedIdentifier()
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
        if (!source.isAtEnd && source.current == '`') source.advance()
        return TokenKind.IDENTIFIER
    }

    private fun isFraction() = source.current == '.' && source.text.getOrNull(source.offset + 1)?.isDigit() == true

    private fun advanceWhile(test: (Char) -> Boolean) {
        while (!source.isAtEnd && test(source.current)) source.advance()
    }
}
