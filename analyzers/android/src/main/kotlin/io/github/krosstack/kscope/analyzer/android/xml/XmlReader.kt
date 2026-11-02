package io.github.krosstack.kscope.analyzer.android.xml

import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/**
 * Reads XML with the JDK's StAX parser, which reports the line and column at the end of each event. The start of a
 * tag and the position of each attribute value are found from the tag text itself, so that a reference on its own
 * line of a multi-line tag is located on that line.
 */
internal class XmlReader {
    private val factory = XMLInputFactory.newFactory().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        setProperty(XMLInputFactory.IS_COALESCING, true)
    }

    fun parse(text: String): XmlDocument {
        val lines = LineIndex(text)
        val reader = factory.createXMLStreamReader(StringReader(text))
        val builder = TreeBuilder(text, lines, reader)
        val error = try {
            builder.run()
            null
        } catch (failure: XMLStreamException) {
            "line ${failure.location?.lineNumber}: ${failure.message?.substringAfter("Message: ")?.trim()}"
        } finally {
            reader.close()
        }
        return XmlDocument(builder.root, error, text, lines)
    }
}

private val attributeSyntax = Regex("""([^\s=/<>"']+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

private class TreeBuilder(
    private val text: String,
    private val lines: LineIndex,
    private val reader: XMLStreamReader,
) {
    var root: XmlNode? = null
        private set
    private val open = ArrayDeque<XmlNode>()
    private var previousEnd = 0

    fun run() {
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> start()
                XMLStreamConstants.END_ELEMENT -> end()
                XMLStreamConstants.CHARACTERS -> characters()
                else -> Unit
            }
            previousEnd = currentOffset()
        }
    }

    private fun currentOffset(): Int =
        lines.offsetOf(reader.location.lineNumber, reader.location.columnNumber).coerceIn(0, text.length)

    private fun start() {
        val tagEnd = currentOffset()
        val tagStart = text.lastIndexOf('<', tagEnd - 1).coerceAtLeast(0)
        val node = XmlNode(
            namespace = reader.namespaceURI?.takeIf { it.isNotEmpty() },
            name = reader.localName,
            attributes = attributesOf(tagStart, tagEnd),
            startOffset = tagStart,
        )
        node.endOffset = tagEnd
        val parent = open.lastOrNull()
        when {
            parent != null -> parent.children += node
            root == null -> root = node
        }
        open.addLast(node)
    }

    private fun end() {
        open.removeLastOrNull()?.endOffset = currentOffset()
    }

    private fun characters() {
        val value = reader.text.trim()
        val parent = open.lastOrNull()
        if (value.isEmpty() || parent == null) return
        val raw = text.substring(previousEnd, currentOffset().coerceAtLeast(previousEnd))
        val inRaw = raw.indexOf(value).takeIf { it >= 0 } ?: raw.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        parent.texts += XmlText(value, previousEnd + inRaw)
    }

    private fun attributesOf(tagStart: Int, tagEnd: Int): List<XmlAttribute> {
        val tagText = text.substring(tagStart, tagEnd)
        val valueStarts = attributeSyntax.findAll(tagText).associate { match ->
            val value = match.groups[2] ?: checkNotNull(match.groups[3])
            match.groupValues[1] to tagStart + value.range.first
        }
        return List(reader.attributeCount) { index ->
            val prefix = reader.getAttributePrefix(index)
            val local = reader.getAttributeLocalName(index)
            val qualified = if (prefix.isNullOrEmpty()) local else "$prefix:$local"
            XmlAttribute(
                namespace = reader.getAttributeNamespace(index)?.takeIf { it.isNotEmpty() },
                name = local,
                value = reader.getAttributeValue(index),
                valueStart = valueStarts[qualified] ?: tagStart,
            )
        }
    }
}
