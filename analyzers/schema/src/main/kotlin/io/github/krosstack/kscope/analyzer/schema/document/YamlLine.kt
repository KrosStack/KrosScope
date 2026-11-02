package io.github.krosstack.kscope.analyzer.schema.document

private const val BYTE_ORDER_MARK = '\uFEFF'

/**
 * One line of a YAML text without its line break: its [number] from 1, the [indent] in characters, the [text] after the
 * indent without trailing white space, and the offset [start] of that text.
 */
internal class YamlLine(val number: Int, val indent: Int, val text: String, val start: Int) {
    val isSkippable: Boolean get() = text.isEmpty() || text.startsWith("#")

    val end: Int get() = start + text.length
}

/** The lines of [text]; a byte order mark at the start is left out of the first line. */
internal fun yamlLinesOf(text: String): List<YamlLine> {
    val lines = ArrayList<YamlLine>()
    var lineStart = 0
    var number = 1
    while (lineStart <= text.length) {
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        lines += lineAt(text, number++, lineStart, lineEnd)
        lineStart = lineEnd + 1
    }
    return lines
}

private fun lineAt(text: String, number: Int, from: Int, to: Int): YamlLine {
    val begin = if (from == 0 && text.startsWith(BYTE_ORDER_MARK)) 1 else from
    val content = text.substring(minOf(begin, to), to).trimEnd()
    val indent = content.length - content.trimStart(' ', '\t').length
    return YamlLine(number, indent, content.substring(indent), begin + indent)
}

/** The lines of the first document of a stream: after a leading `---` and `%` directives, up to the next marker. */
internal fun firstDocumentOf(lines: List<YamlLine>): List<YamlLine> {
    val document = ArrayList<YamlLine>()
    for (line in lines) {
        val isMarker = line.indent == 0 && isDocumentMarker(line.text)
        val isDirective = document.isEmpty() && line.text.startsWith("%")
        if (isMarker && document.any { !it.isSkippable }) break
        if (!isMarker && !isDirective) document += line
    }
    return document
}

private fun isDocumentMarker(text: String): Boolean {
    if (!text.startsWith("---") && !text.startsWith("...")) return false
    val rest = text.substring(3)
    val isSeparated = rest.isEmpty() || rest[0].isWhitespace()
    return isSeparated && rest.trimStart().let { it.isEmpty() || it.startsWith("#") }
}
