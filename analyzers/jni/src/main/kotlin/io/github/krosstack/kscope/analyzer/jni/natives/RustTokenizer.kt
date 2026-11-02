package io.github.krosstack.kscope.analyzer.jni.natives

import io.github.krosstack.kscope.analyzer.jni.lexing.SourceToken
import io.github.krosstack.kscope.analyzer.jni.lexing.TextCursor
import io.github.krosstack.kscope.analyzer.jni.lexing.TokenKind
import io.github.krosstack.kscope.model.TextRange

private val rawStringPrefixes = setOf("r", "br", "cr")
private val stringPrefixes = setOf("b", "c")
private const val ESCAPE_PREFIX = 3
private const val LONGEST_ESCAPED_CHARACTER = 12

private fun isLineBreak(character: Char) = character == '\n' || character == '\r'

private fun isIdentifierStart(character: Char) = character.isLetter() || character == '_'

private fun isIdentifierPart(character: Char) = isIdentifierStart(character) || character.isDigit()

/** Skips what is not code in Rust: white space, line comments and block comments, which nest. */
private class RustTrivia(private val source: TextCursor) {
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

/** Reads the literals of Rust that hide their content from the code around them. */
private class RustLiterals(private val source: TextCursor) {
    fun hashesBeforeQuote(): Int? {
        var index = source.offset
        while (source.text.getOrNull(index) == '#') index++
        return (index - source.offset).takeIf { source.text.getOrNull(index) == '"' }
    }

    fun rawString(hashes: Int) {
        val terminator = "\"" + "#".repeat(hashes)
        val close = source.text.indexOf(terminator, source.offset + hashes + 1)
        source.advanceTo(if (close < 0) source.text.length else close + terminator.length)
    }

    fun string() {
        val text = source.text
        source.advance()
        while (!source.isAtEnd && text[source.offset] != '"') {
            if (text[source.offset] == '\\' && source.offset + 1 < text.length) source.advance()
            source.advance()
        }
        if (!source.isAtEnd) source.advance()
    }

    /** True when a character literal was read; a lone quote, as in a lifetime, is left for the caller. */
    fun character(): Boolean {
        val length = characterLength()
        source.advanceTo(source.offset + length)
        return length > 0
    }

    private fun characterLength(): Int {
        val quote = source.offset
        val first = source.text.getOrNull(quote + 1)
        return when {
            first == null || first == '\'' || isLineBreak(first) -> 0
            first == '\\' -> escapedLength(quote)
            else -> plainLength(quote)
        }
    }

    private fun escapedLength(quote: Int): Int {
        val close = source.text.indexOf('\'', quote + ESCAPE_PREFIX)
        val isShort = close in 0..quote + LONGEST_ESCAPED_CHARACTER
        return if (isShort && source.text.substring(quote, close).none(::isLineBreak)) close - quote + 1 else 0
    }

    private fun plainLength(quote: Int): Int {
        val width = Character.charCount(source.text.codePointAt(quote + 1))
        return if (source.text.getOrNull(quote + 1 + width) == '\'') width + 2 else 0
    }
}

/**
 * Splits Rust text into identifiers, numbers, string and character literals, and single-character punctuation.
 * Comments and white space are dropped, block comments nest, and the content of a literal is one token, whether it is
 * a string, a raw string with any number of `#`, a byte or C string, or a character: nothing in a comment or a
 * literal is taken for code. A quote that opens no character literal, as in a lifetime, is punctuation. Unterminated
 * comments and literals end at the end of the file; the tokenizer never fails. Lines and columns start at 1.
 */
internal class RustTokenizer(text: String) {
    private val source = TextCursor(text)
    private val trivia = RustTrivia(source)
    private val literals = RustLiterals(source)

    fun tokens(): List<SourceToken> {
        val tokens = mutableListOf<SourceToken>()
        while (true) {
            trivia.skip()
            if (source.isAtEnd) return tokens
            tokens += token()
        }
    }

    private fun token(): SourceToken {
        val text = source.text
        val start = source.offset
        val startLine = source.line
        val startColumn = source.column
        val kind = when {
            isIdentifierStart(text[start]) -> identifier()
            text[start].isDigit() -> number()
            text[start] == '"' -> TokenKind.STRING.also { literals.string() }
            text[start] == '\'' -> quote()
            else -> TokenKind.PUNCTUATION.also { source.advance() }
        }
        val range = TextRange(startLine, startColumn, source.line, source.column)
        return SourceToken(kind, text.substring(start, source.offset), range, start, source.offset)
    }

    private fun identifier(): TokenKind {
        val start = source.offset
        advanceWhile(::isIdentifierPart)
        return literalAfter(source.text.substring(start, source.offset)) ?: TokenKind.IDENTIFIER
    }

    private fun literalAfter(prefix: String): TokenKind? = when (prefix) {
        in rawStringPrefixes -> rawLiteral(prefix)
        in stringPrefixes -> quotedLiteral(prefix)
        else -> null
    }

    private fun rawLiteral(prefix: String): TokenKind? {
        val hashes = literals.hashesBeforeQuote()
        val isRawIdentifier = prefix == "r" && source.text.getOrNull(source.offset) == '#' &&
            source.text.getOrNull(source.offset + 1)?.let(::isIdentifierStart) == true
        return when {
            hashes != null -> TokenKind.STRING.also { literals.rawString(hashes) }
            isRawIdentifier -> rawIdentifier()
            else -> null
        }
    }

    private fun quotedLiteral(prefix: String): TokenKind? = when (source.text.getOrNull(source.offset)) {
        '"' -> TokenKind.STRING.also { literals.string() }
        '\'' -> if (prefix == "b") quote() else null
        else -> null
    }

    private fun rawIdentifier(): TokenKind {
        source.advance()
        advanceWhile(::isIdentifierPart)
        return TokenKind.IDENTIFIER
    }

    private fun quote(): TokenKind {
        if (literals.character()) return TokenKind.STRING
        source.advance()
        return TokenKind.PUNCTUATION
    }

    private fun number(): TokenKind {
        advanceWhile { isIdentifierPart(it) || isFraction() }
        return TokenKind.NUMBER
    }

    private fun isFraction() =
        source.text[source.offset] == '.' && source.text.getOrNull(source.offset + 1)?.isDigit() == true

    private fun advanceWhile(test: (Char) -> Boolean) {
        while (!source.isAtEnd && test(source.text[source.offset])) source.advance()
    }
}
