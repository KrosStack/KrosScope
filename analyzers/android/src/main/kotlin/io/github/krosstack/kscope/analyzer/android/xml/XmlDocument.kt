package io.github.krosstack.kscope.analyzer.android.xml

import io.github.krosstack.kscope.model.TextRange

/** The value of an attribute and where it starts in the text; the value excludes the quotes. */
internal class XmlAttribute(val namespace: String?, val name: String, val value: String, val valueStart: Int)

/** A text inside an element, trimmed, and where it starts in the text. */
internal class XmlText(val value: String, val start: Int)

internal class XmlNode(
    val namespace: String?,
    val name: String,
    val attributes: List<XmlAttribute>,
    val startOffset: Int,
) {
    val children = mutableListOf<XmlNode>()
    val texts = mutableListOf<XmlText>()
    var endOffset: Int = startOffset
        internal set

    fun attribute(name: String, namespace: String? = null): XmlAttribute? =
        attributes.firstOrNull { it.name == name && it.namespace == namespace }
}

/** The elements of a file, as far as they could be read: [error] says why reading stopped early, if it did. */
internal class XmlDocument(val root: XmlNode?, val error: String?, val text: String, private val lines: LineIndex) {
    fun range(start: Int, end: Int): TextRange = lines.range(start, end)

    fun wholeFile(): TextRange = lines.range(0, text.length)
}
