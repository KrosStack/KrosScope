package io.github.krosstack.kscope.analyzer.schema.proto

import com.squareup.wire.schema.Location
import io.github.krosstack.kscope.analyzer.schema.Span
import io.github.krosstack.kscope.analyzer.schema.TextIndex

/**
 * Where proto elements lie in the text of their file. The parser reports only the start of an element, so the end is
 * found by scanning for the `;` that ends a statement or the `}` that closes a block, skipping strings, comments and
 * option brackets, and the name is found by looking for it after the keyword.
 */
internal class ProtoSpans(private val text: String) {
    private val index = TextIndex(text)

    fun placeOf(location: Location, name: String, documentation: String?, isAssignment: Boolean): ProtoPlace {
        val start = index.offsetOf(location.line, location.column)
        val declaration = Span(start, endOf(start))
        return ProtoPlace(declaration, nameSpanIn(declaration, name, isAssignment), documentation)
    }

    private fun nameSpanIn(declaration: Span, name: String, isAssignment: Boolean): Span {
        val escaped = Regex.escape(name)
        val pattern = if (isAssignment) """\b$escaped\b(?=\s*=)""" else """\b$escaped\b"""
        val searchStart = if (isAssignment) declaration.start else afterKeyword(declaration.start)
        val found = Regex(pattern).find(text.substring(0, declaration.end), searchStart)
        return found?.let { Span(it.range.first, it.range.last + 1) } ?: Span(declaration.start, declaration.start)
    }

    private fun afterKeyword(start: Int): Int =
        (start until text.length).firstOrNull { text[it].isWhitespace() } ?: text.length

    private fun endOf(start: Int): Int {
        var brackets = 0
        val terminator = codeCharacters(start).firstOrNull { (_, character) ->
            when (character) {
                '[' -> false.also { brackets++ }
                ']' -> false.also { brackets-- }
                ';', '{' -> brackets == 0
                else -> false
            }
        }
        return when {
            terminator == null -> text.length
            terminator.second == ';' -> terminator.first + 1
            else -> endOfBlock(terminator.first)
        }
    }

    private fun endOfBlock(open: Int): Int {
        var depth = 0
        val closing = codeCharacters(open).firstOrNull { (_, character) ->
            when (character) {
                '{' -> false.also { depth++ }
                '}' -> --depth == 0
                else -> false
            }
        }
        return closing?.let { it.first + 1 } ?: text.length
    }

    private fun codeCharacters(from: Int): Sequence<Pair<Int, Char>> = sequence {
        var cursor = from
        while (cursor < text.length) {
            val opaqueEnd = endOfOpaque(cursor)
            if (opaqueEnd == null) {
                yield(cursor to text[cursor])
                cursor++
            } else {
                cursor = opaqueEnd
            }
        }
    }

    private fun endOfOpaque(at: Int): Int? = when {
        text[at] == '"' || text[at] == '\'' -> endOfString(at)
        text.startsWith("//", at) -> endOfLine(at)
        text.startsWith("/*", at) -> endOfBlockComment(at)
        else -> null
    }

    private fun endOfString(open: Int): Int {
        val quote = text[open]
        var cursor = open + 1
        while (cursor < text.length && text[cursor] != quote) cursor += if (text[cursor] == '\\') 2 else 1
        return minOf(cursor + 1, text.length)
    }

    private fun endOfLine(from: Int): Int = text.indexOf('\n', from).let { if (it < 0) text.length else it }

    private fun endOfBlockComment(open: Int): Int =
        text.indexOf("*/", open + 2).let { if (it < 0) text.length else it + 2 }
}
