package io.github.krosstack.kscope.analyzer.compose

import io.github.krosstack.kscope.model.TextRange
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

private const val ELEMENT_DEPTH = 2
private val attributeSyntax = Regex("""([^\s=/<>"']+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

/** Converts offsets of a text to the 1-based lines and columns of the model; `\r\n`, `\n` and `\r` end a line. */
internal class LineIndex(text: String) {
    private val lineStarts: IntArray = lineStartsOf(text)

    fun offsetOf(line: Int, column: Int): Int = lineStarts[(line - 1).coerceIn(0, lineStarts.lastIndex)] + column - 1

    fun range(start: Int, end: Int): TextRange {
        val (startLine, startColumn) = positionOf(start)
        val (endLine, endColumn) = positionOf(end)
        return TextRange(startLine, startColumn, endLine, endColumn)
    }

    private fun positionOf(offset: Int): Pair<Int, Int> {
        val found = lineStarts.binarySearch(offset)
        val index = if (found >= 0) found else -found - 2
        return index + 1 to offset - lineStarts[index] + 1
    }

    private companion object {
        fun lineStartsOf(text: String): IntArray {
            val starts = mutableListOf(0)
            var index = 0
            while (index < text.length) {
                if (text[index] == '\r' && text.getOrNull(index + 1) == '\n') index++
                if (text[index] == '\n' || text[index] == '\r') starts += index + 1
                index++
            }
            return starts.toIntArray()
        }
    }
}

/** An element that is a child of the root of a `values` file, with the places of its start, end and name. */
internal class ValuesElement(val tag: String, val name: String, val declaration: TextRange, val nameRange: TextRange)

/** The elements of a `values` file as far as they could be read; [problem] says why reading stopped early. */
internal class ScannedValues(val elements: List<ValuesElement>, val problem: String?)

/**
 * Reads the children of the root element of a `values` file with the JDK's StAX parser, which reports the line and
 * column at the end of each event: the start of a tag and the value of its `name` are found from the tag text.
 */
internal class ValuesScanner {
    private val factory = XMLInputFactory.newFactory().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    }

    fun scan(text: String): ScannedValues {
        val lines = LineIndex(text)
        val reader = factory.createXMLStreamReader(StringReader(text))
        val collected = mutableListOf<ValuesElement>()
        val problem = try {
            Walk(text, lines, reader, collected).run()
            null
        } catch (failure: XMLStreamException) {
            "line ${failure.location?.lineNumber}: ${failure.message?.substringAfter("Message: ")?.trim()}"
        } finally {
            reader.close()
        }
        return ScannedValues(collected, problem)
    }
}

private class OpenElement(val tag: String, val name: String?, val start: Int, val nameRange: IntRange?)

private class Walk(
    private val text: String,
    private val lines: LineIndex,
    private val reader: XMLStreamReader,
    private val out: MutableList<ValuesElement>,
) {
    private val open = ArrayDeque<OpenElement>()

    fun run() {
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> start()
                XMLStreamConstants.END_ELEMENT -> end()
                else -> Unit
            }
        }
    }

    private fun offset(): Int =
        lines.offsetOf(reader.location.lineNumber, reader.location.columnNumber).coerceIn(0, text.length)

    private fun start() {
        val tagEnd = offset()
        val tagStart = text.lastIndexOf('<', tagEnd - 1).coerceAtLeast(0)
        val isChildOfRoot = open.size == ELEMENT_DEPTH - 1
        val name = if (isChildOfRoot) reader.getAttributeValue(null, "name") else null
        val nameRange = if (isChildOfRoot && name != null) nameRangeOf(tagStart, tagEnd) else null
        open.addLast(OpenElement(reader.localName, name, tagStart, nameRange))
    }

    private fun end() {
        val element = open.removeLastOrNull() ?: return
        val isChildOfRoot = open.size == ELEMENT_DEPTH - 1
        if (isChildOfRoot && element.name != null) {
            val range = element.nameRange ?: element.start..element.start
            out += ValuesElement(
                tag = element.tag,
                name = element.name.trim(),
                declaration = lines.range(element.start, offset()),
                nameRange = lines.range(range.first, range.last + 1),
            )
        }
    }

    private fun nameRangeOf(tagStart: Int, tagEnd: Int): IntRange? {
        val tagText = text.substring(tagStart, tagEnd)
        val match = attributeSyntax.findAll(tagText).firstOrNull { it.groupValues[1] == "name" }
        val value = match?.let { it.groups[2] ?: it.groups[3] }
        return value?.let { (tagStart + it.range.first)..(tagStart + it.range.last) }
    }
}
