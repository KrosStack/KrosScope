package io.github.krosstack.kscope.analyzer.schema.document

import io.github.krosstack.kscope.analyzer.schema.TextIndex

/** The lines of the first document of a YAML text and the place that a reader has come to in them. */
internal class YamlCursor(text: String) {
    private val index = TextIndex(text)
    private val lines: MutableList<YamlLine> = firstDocumentOf(yamlLinesOf(text)).toMutableList()
    private var position = 0

    /** The next line that is neither blank nor a comment, which the cursor moves up to; null at the end. */
    fun next(): YamlLine? {
        while (position < lines.size && lines[position].isSkippable) position++
        return lines.getOrNull(position)
    }

    /** The line at the cursor, whatever it holds; null at the end. */
    fun current(): YamlLine? = lines.getOrNull(position)

    fun advance() {
        position++
    }

    /** Takes the line at the cursor as the part of it that starts at [column]: the content of a sequence item. */
    fun narrowTo(column: Int) {
        val line = lines[position]
        lines[position] = YamlLine(line.number, line.indent + column, line.text.substring(column), line.start + column)
    }

    /** Moves the cursor past every line that ends at or before the offset. */
    fun skipPast(offset: Int) {
        val last = index.lineOf(offset)
        while (position < lines.size && lines[position].number <= last) position++
    }
}
