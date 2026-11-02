package io.github.krosstack.kscope.analyzer.jni.natives

import io.github.krosstack.kscope.analyzer.jni.lexing.SourceToken
import io.github.krosstack.kscope.analyzer.jni.lexing.TextCursor
import io.github.krosstack.kscope.analyzer.jni.lexing.TokenKind
import io.github.krosstack.kscope.model.TextRange

private val rawStringPrefixes = setOf("R", "LR", "u8R", "uR", "UR")

private fun isLineBreak(character: Char) = character == '\n' || character == '\r'

private fun isIdentifierStart(character: Char) = character.isLetter() || character == '_' || character == '$'

private fun isIdentifierPart(character: Char) = isIdentifierStart(character) || character.isDigit()

private fun isNumberPart(character: Char) = isIdentifierPart(character) || character == '.' || character == '\''

/** Skips what is not code: white space, comments and preprocessor directives. */
private class CTrivia(private val source: TextCursor) {
    fun skip() {
        val text = source.text
        while (!source.isAtEnd) {
            when {
                text[source.offset] == '#' && source.isLineStart -> skipDirective()
                source.startsWith("//") -> skipLineComment()
                source.startsWith("/*") -> skipBlockComment()
                text[source.offset].isWhitespace() -> source.advance()
                else -> return
            }
        }
    }

    private fun skipDirective() {
        while (!source.isAtEnd) {
            if (source.startsWith("/*")) {
                skipBlockComment()
            } else if (isLineBreak(source.text[source.offset]) && !isEscapedLineBreak()) {
                return
            } else {
                source.advance()
            }
        }
    }

    private fun isEscapedLineBreak(): Boolean {
        val text = source.text
        val offset = source.offset
        val before = if (offset >= 2 && text[offset] == '\n' && text[offset - 1] == '\r') offset - 2 else offset - 1
        return before >= 0 && text[before] == '\\'
    }

    private fun skipLineComment() {
        while (!source.isAtEnd && !isLineBreak(source.text[source.offset])) source.advance()
    }

    private fun skipBlockComment() {
        val close = source.text.indexOf("*/", source.offset + 2)
        source.advanceTo(if (close < 0) source.text.length else close + 2)
    }
}

/**
 * Splits C and C++ text into identifiers, numbers, string and character literals, and single-character punctuation.
 * Comments, white space and preprocessor directives are dropped, and the content of a literal is one token, so that
 * nothing in a comment or a string is taken for code. Unterminated comments and literals end at the end of the file
 * or of the line; the tokenizer never fails. Lines and columns start at 1.
 */
internal class CTokenizer(text: String) {
    private val source = TextCursor(text)
    private val trivia = CTrivia(source)

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
            text[start] == '"' || text[start] == '\'' -> literal()
            else -> TokenKind.PUNCTUATION.also { source.advance() }
        }
        val range = TextRange(startLine, startColumn, source.line, source.column)
        return SourceToken(kind, text.substring(start, source.offset), range, start, source.offset)
    }

    private fun identifier(): TokenKind {
        val start = source.offset
        while (!source.isAtEnd && isIdentifierPart(source.text[source.offset])) source.advance()
        val prefix = source.text.substring(start, source.offset)
        val isRawString = source.text.getOrNull(source.offset) == '"' && prefix in rawStringPrefixes
        return if (isRawString) rawString() else TokenKind.IDENTIFIER
    }

    private fun number(): TokenKind {
        while (!source.isAtEnd && isNumberPart(source.text[source.offset])) source.advance()
        return TokenKind.NUMBER
    }

    private fun literal(): TokenKind {
        val text = source.text
        val quote = text[source.offset]
        source.advance()
        while (!source.isAtEnd && text[source.offset] != quote && !isLineBreak(text[source.offset])) {
            val isEscape = text[source.offset] == '\\' && !isLineBreak(text.getOrElse(source.offset + 1) { '\n' })
            if (isEscape) source.advance()
            source.advance()
        }
        if (!source.isAtEnd && text[source.offset] == quote) source.advance()
        return TokenKind.STRING
    }

    private fun rawString(): TokenKind {
        val text = source.text
        source.advance()
        val open = text.indexOf('(', source.offset)
        val delimiter = if (open < 0) "" else text.substring(source.offset, open)
        val close = text.indexOf(")$delimiter\"", source.offset)
        source.advanceTo(if (open < 0 || close < 0) text.length else close + delimiter.length + 2)
        return TokenKind.STRING
    }
}
