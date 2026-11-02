package io.github.krosstack.kscope.analyzer.schema.document

import io.github.krosstack.kscope.analyzer.schema.Span
import io.github.krosstack.kscope.analyzer.schema.TextIndex

internal fun readDocument(text: String): DocNode =
    checkNotNull(YamlReader(text).read()) { "Nothing was read from: $text" }

internal fun DocNode.child(key: String): DocEntry = checkNotNull((this as DocMapping).entry(key)) { "No $key in $this" }

internal fun DocNode.at(vararg keys: String): DocNode = keys.fold(this) { node, key -> node.child(key).value }

internal fun DocNode.entryAt(vararg keys: String): DocEntry =
    keys.dropLast(1).fold(this) { node, key -> node.child(key).value }.child(keys.last())

internal fun DocNode.scalar(): String = (this as DocScalar).text

internal fun DocNode.items(): List<DocNode> = (this as DocSequence).items

internal fun DocNode.texts(): List<String> = items().map { it.scalar() }

internal fun DocNode.keys(): List<String> = (this as DocMapping).entries.map { it.key }

internal fun lineOf(text: String, span: Span): Int = TextIndex(text).lineOf(span.start)
