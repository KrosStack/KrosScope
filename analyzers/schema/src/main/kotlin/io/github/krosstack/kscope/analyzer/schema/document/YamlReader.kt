package io.github.krosstack.kscope.analyzer.schema.document

import io.github.krosstack.kscope.analyzer.schema.Span

private const val MAX_DEPTH = 200

/**
 * Reads the block style of YAML that API descriptions use: mappings and sequences by indentation, with sequences
 * allowed at the indentation of their key, plain, quoted and block scalars, flow collections, comments, anchors and
 * tags, which are skipped. A flow collection, such as a JSON document, is read by [FlowReader]. It reads the first
 * document of a stream and never fails; what is not YAML of this kind reads as scalars or is left out. Every node
 * keeps the span of the text it covers.
 */
internal class YamlReader(text: String) {
    private val cursor = YamlCursor(text)
    private val scalars = YamlScalarReader(text, cursor)
    private var depth = 0

    fun read(): DocNode? = block(0)

    private fun block(minimum: Int, parent: Int = minimum - 1): DocNode? {
        depth++
        val node = if (depth > MAX_DEPTH) skipLine() else blockAt(minimum, parent)
        depth--
        return node
    }

    private fun skipLine(): DocNode? = cursor.next()?.also { cursor.advance() }?.let { empty(it) }

    private fun blockAt(minimum: Int, parent: Int): DocNode? {
        val line = cursor.next() ?: return null
        return when {
            line.indent < minimum -> null
            isSequenceItem(line.text) -> sequence(line.indent)
            keyOf(line.text) != null -> mapping(line.indent)
            else -> scalarBlock(line, parent)
        }
    }

    private fun scalarBlock(line: YamlLine, parent: Int): DocNode {
        cursor.advance()
        return value(line, 0, parent, false)
    }

    private fun mapping(indent: Int): DocMapping {
        val entries = ArrayList<DocEntry>()
        var line = cursor.next()
        var key = line?.let { keyOf(it.text) }
        while (line != null && key != null && line.indent == indent) {
            cursor.advance()
            val value = value(line, key.valueColumn, indent, true)
            entries += DocEntry(key.name, Span(line.start + key.start, line.start + key.end), value)
            line = cursor.next()
            key = line?.let { keyOf(it.text) }
        }
        return DocMapping(entries, spanBetween(entries.firstOrNull()?.keySpan, entries.lastOrNull()?.value))
    }

    private fun sequence(indent: Int): DocSequence {
        val items = ArrayList<DocNode>()
        val start = cursor.next()?.start ?: 0
        var line = cursor.next()
        while (line != null && line.indent == indent && isSequenceItem(line.text)) {
            val column = 1 + line.text.substring(1).takeWhile { it.isWhitespace() }.length
            val isEmpty = valueColumn(line.text, column) >= line.text.length
            items += if (isEmpty) emptyItem(line, indent) else item(line, column, indent)
            line = cursor.next()
        }
        return DocSequence(items, spanBetween(Span(start, start), items.lastOrNull()))
    }

    private fun item(line: YamlLine, column: Int, indent: Int): DocNode {
        cursor.narrowTo(column)
        return block(0, indent) ?: empty(line)
    }

    private fun emptyItem(line: YamlLine, indent: Int): DocNode {
        cursor.advance()
        return nested(line, indent, false)
    }

    private fun value(line: YamlLine, column: Int, parent: Int, siblingSequence: Boolean): DocNode {
        val at = valueColumn(line.text, column)
        val first = line.text.getOrNull(at)
        return when {
            first == null -> nested(line, parent, siblingSequence)
            isBlockScalarHeader(line.text, at) -> scalars.blockScalar(line, at, parent)
            first == '[' || first == '{' -> scalars.flow(line, at)
            first == '"' || first == '\'' -> scalars.quoted(line, at)
            else -> scalars.plain(line, at, parent)
        }
    }

    private fun nested(line: YamlLine, parent: Int, siblingSequence: Boolean): DocNode {
        val next = cursor.next()
        return when {
            next == null -> empty(line)
            next.indent > parent -> block(parent + 1) ?: empty(line)
            siblingSequence && next.indent == parent && isSequenceItem(next.text) -> sequence(parent)
            else -> empty(line)
        }
    }
}

private fun empty(line: YamlLine) = DocScalar("", Span(line.end, line.end))

private fun spanBetween(first: Span?, last: DocNode?): Span =
    Span(first?.start ?: 0, maxOf(last?.span?.end ?: 0, first?.end ?: 0))
