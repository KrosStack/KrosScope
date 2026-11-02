package io.github.krosstack.kscope.analyzer.gradle.catalog

import io.github.krosstack.kscope.analyzer.gradle.script.LineIndex
import io.github.krosstack.kscope.model.TextRange

/** The declaration of a catalog entry as a range of the TOML text: from its key to the end of its value. */
internal class EntryDeclaration(val declaration: TextRange, val name: TextRange)

/**
 * Finds where a catalog entry ends, given the line its key is on. A value can span lines when it is an array or an
 * inline table, so the end is found by balancing brackets and skipping strings and comments.
 */
internal class EntryRanges(private val text: String, private val lines: LineIndex) {
    private val strings = TomlStrings(text)

    fun at(line: Int): EntryDeclaration {
        val lineStart = lines.lineStart(line) ?: return EntryDeclaration(EMPTY, EMPTY)
        val start = firstNonBlank(lineStart)
        return if (text.startsWith("[", start)) header(start) else entry(start)
    }

    private fun header(start: Int): EntryDeclaration {
        val range = lines.rangeOf(start, lineEnd(start))
        return EntryDeclaration(range, range)
    }

    private fun entry(start: Int): EntryDeclaration {
        val equals = text.indexOf('=', start)
        val keyEnd = if (equals < 0) start else trimEnd(start, equals)
        return EntryDeclaration(lines.rangeOf(start, valueEnd(start, equals)), lines.rangeOf(start, keyEnd))
    }

    private fun firstNonBlank(from: Int): Int {
        var index = from
        while (index < text.length && (text[index] == ' ' || text[index] == '\t')) index++
        return index
    }

    private fun lineEnd(from: Int): Int {
        val newline = text.indexOf('\n', from)
        return trimEnd(from, if (newline < 0) text.length else newline)
    }

    private fun trimEnd(start: Int, end: Int): Int {
        var index = end
        while (index > start && text[index - 1].isWhitespace()) index--
        return index
    }

    private fun valueEnd(start: Int, equals: Int): Int {
        val newline = text.indexOf('\n', start)
        val keyIsAlone = equals < 0 || newline in 0 until equals
        return if (keyIsAlone) lineEnd(start) else trimEnd(start, scanValue(equals + 1))
    }

    private fun scanValue(from: Int): Int {
        var depth = 0
        var index = from
        while (index < text.length && !endsValue(index, depth)) {
            depth += bracketDelta(text[index])
            index = if (text[index] == '"' || text[index] == '\'') strings.after(index) else index + 1
        }
        return index
    }

    private fun endsValue(index: Int, depth: Int): Boolean = depth <= 0 && (text[index] == '\n' || text[index] == '#')

    private fun bracketDelta(character: Char): Int = when (character) {
        '[', '{' -> 1
        ']', '}' -> -1
        else -> 0
    }

    private companion object {
        val EMPTY = TextRange(1, 1, 1, 1)
    }
}
