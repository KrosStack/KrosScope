package io.github.krosstack.kscope.analyzer.schema.openapi

import io.github.krosstack.kscope.analyzer.schema.document.DocMapping
import io.github.krosstack.kscope.analyzer.schema.document.DocNode
import io.github.krosstack.kscope.analyzer.schema.document.DocScalar
import io.github.krosstack.kscope.analyzer.schema.document.DocSequence

private val shapedElsewhere = setOf("allOf", "oneOf", "anyOf", "enum")

/** Reads the properties of a plain object schema: one that lists `properties` and is no enumeration or composition. */
internal object OpenApiSchemaReader {
    fun propertiesOf(node: DocNode): List<OpenApiProperty> {
        val schema = node as? DocMapping
        val properties = schema?.mapping("properties")
        return if (schema == null || properties == null || shapedElsewhere.any { schema.entry(it) != null }) {
            emptyList()
        } else {
            val required = requiredNames(schema)
            properties.entries.map { OpenApiProperty(it.key, it.key in required, OpenApiTypeReader.typeOf(it.value)) }
        }
    }

    private fun requiredNames(schema: DocMapping): Set<String> =
        (schema.entry("required")?.value as? DocSequence)?.items.orEmpty()
            .mapNotNull { (it as? DocScalar)?.text }.toSet()
}
