package io.github.krosstack.kscope.analyzer.schema.web.map

import io.github.krosstack.kscope.analyzer.schema.document.DocMapping
import io.github.krosstack.kscope.analyzer.schema.document.DocScalar
import io.github.krosstack.kscope.analyzer.schema.document.DocSequence
import io.github.krosstack.kscope.analyzer.schema.document.FlowReader

private const val BYTE_ORDER_MARK = "\uFEFF"
private const val SECURITY_PREFIX = ")]}'"

/** Reads a source map of version 3. Text that is no JSON object with a list of `sources` has no value. */
internal object SourceMapReader {
    fun read(text: String): SourceMap? {
        val body = text.removePrefix(BYTE_ORDER_MARK).trimStart().removePrefix(SECURITY_PREFIX)
        val map = FlowReader(body, 0).read() as? DocMapping
        val sources = map?.entry("sources")?.value as? DocSequence
        if (map == null || sources == null) return null
        return SourceMap(
            file = map.text("file")?.takeIf { it.isNotEmpty() },
            sourceRoot = map.text("sourceRoot").orEmpty(),
            sources = sources.items.mapNotNull { (it as? DocScalar)?.text },
        )
    }
}
