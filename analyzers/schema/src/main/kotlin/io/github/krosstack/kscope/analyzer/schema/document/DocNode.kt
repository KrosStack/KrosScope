package io.github.krosstack.kscope.analyzer.schema.document

import io.github.krosstack.kscope.analyzer.schema.Span

/** A value of a YAML or JSON document and the span of text it covers. */
internal sealed interface DocNode {
    val span: Span
}

/** A scalar as text; the span of a quoted scalar leaves out the quotes. */
internal class DocScalar(val text: String, override val span: Span) : DocNode

internal class DocSequence(val items: List<DocNode>, override val span: Span) : DocNode

internal class DocMapping(val entries: List<DocEntry>, override val span: Span) : DocNode {
    fun entry(key: String): DocEntry? = entries.firstOrNull { it.key == key }

    fun mapping(key: String): DocMapping? = entry(key)?.value as? DocMapping

    fun text(key: String): String? = (entry(key)?.value as? DocScalar)?.text
}

/** A key of a mapping with where it is written, and its value. */
internal class DocEntry(val key: String, val keySpan: Span, val value: DocNode) {
    val span: Span get() = Span(keySpan.start, maxOf(value.span.end, keySpan.end))
}
