package io.github.krosstack.kscope.analyzer.android.aidl.parser

import io.github.krosstack.kscope.model.TextRange

internal enum class AidlTokenKind {
    WORD,
    NUMBER,
    STRING,
    SYMBOL,
    END,
}

/** [start] and [end] are offsets in the text; the end offset is exclusive. */
internal data class AidlToken(
    val kind: AidlTokenKind,
    val text: String,
    val range: TextRange,
    val start: Int,
    val end: Int,
)

internal sealed interface AidlScan {
    /** The tokens of the file, always closed by an [AidlTokenKind.END] token. */
    data class Tokens(val tokens: List<AidlToken>) : AidlScan

    data class Failed(val problem: AidlProblem) : AidlScan
}

private fun isLineBreak(character: Char) = character == '\n' || character == '\r'

private fun isWordStart(character: Char) = character in 'a'..'z' || character in 'A'..'Z' || character == '_'

private fun isWordPart(character: Char) = isWordStart(character) || character in '0'..'9'

/**
 * Splits AIDL text into words, numbers, string literals and single-character symbols. Comments and white space are
 * dropped. Lines and columns start at 1, and `\r\n`, `\n` and `\r` end a line. The first problem, an unterminated
 * comment or string, ends the scan.
 */
internal class AidlScanner(private val text: String) {
    private var offset = 0
    private var line = 1
    private var column = 1
    private var problem: AidlProblem? = null

    fun scan(): AidlScan {
        val tokens = mutableListOf<AidlToken>()
        while (problem == null) {
            skipTrivia()
            if (problem != null || offset >= text.length) break
            tokens += token()
        }
        val failure = problem
        val end = AidlToken(AidlTokenKind.END, "", TextRange(line, column, line, column), text.length, text.length)
        return if (failure != null) AidlScan.Failed(failure) else AidlScan.Tokens(tokens + end)
    }

    private fun skipTrivia() {
        while (problem == null && offset < text.length) {
            when {
                text[offset].isWhitespace() -> advance()
                text.startsWith("//", offset) -> skipLineComment()
                text.startsWith("/*", offset) -> skipBlockComment()
                else -> return
            }
        }
    }

    private fun skipLineComment() {
        while (offset < text.length && !isLineBreak(text[offset])) advance()
    }

    private fun skipBlockComment() {
        val startLine = line
        val startColumn = column
        val close = text.indexOf("*/", offset + 2)
        if (close < 0) {
            problem = AidlProblem("Unterminated comment", TextRange(startLine, startColumn, startLine, startColumn + 2))
            return
        }
        while (offset < close + 2) advance()
    }

    private fun token(): AidlToken {
        val startOffset = offset
        val startLine = line
        val startColumn = column
        val kind = when {
            isWordStart(text[offset]) -> consume(AidlTokenKind.WORD) { isWordPart(it) }
            text[offset].isDigit() -> consume(AidlTokenKind.NUMBER) { isWordPart(it) || it == '.' }
            text[offset] == '"' || text[offset] == '\'' -> consumeString(startLine, startColumn)
            else -> consume(AidlTokenKind.SYMBOL) { false }
        }
        return AidlToken(
            kind = kind,
            text = text.substring(startOffset, offset),
            range = TextRange(startLine, startColumn, line, column),
            start = startOffset,
            end = offset,
        )
    }

    private fun consume(kind: AidlTokenKind, isPart: (Char) -> Boolean): AidlTokenKind {
        advance()
        while (offset < text.length && isPart(text[offset])) advance()
        return kind
    }

    private fun consumeString(startLine: Int, startColumn: Int): AidlTokenKind {
        val quote = text[offset]
        advance()
        while (offset < text.length && text[offset] != quote && !isLineBreak(text[offset])) {
            if (text[offset] == '\\' && offset + 1 < text.length && !isLineBreak(text[offset + 1])) advance()
            advance()
        }
        if (offset < text.length && text[offset] == quote) {
            advance()
        } else {
            problem = AidlProblem("Unterminated string", TextRange(startLine, startColumn, line, column))
        }
        return AidlTokenKind.STRING
    }

    private fun advance() {
        val current = text[offset++]
        val isLineEnd = current == '\n' || (current == '\r' && text.getOrNull(offset) != '\n')
        if (isLineEnd) {
            line++
            column = 1
        } else {
            column++
        }
    }
}
