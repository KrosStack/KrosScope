package io.github.krosstack.kscope.analyzer.schema.openapi

import io.github.krosstack.kscope.analyzer.schema.document.DocMapping
import io.github.krosstack.kscope.analyzer.schema.document.DocNode

internal const val REFERENCE_KEY = "\$ref"
private const val ARRAY = "array"
private val schemaReferences = listOf("#/components/schemas/", "#/definitions/")
private val scalarTypes = setOf("string", "integer", "number", "boolean")

/** Reads the type of a schema node. A reference counts only when it names a schema of the same document. */
internal object OpenApiTypeReader {
    fun typeOf(node: DocNode?): OpenApiType {
        val schema = node as? DocMapping
        val reference = schema?.text(REFERENCE_KEY)?.let(::schemaNameOf)
        val type = schema?.text("type")
        return when {
            schema == null -> OpenApiType.Unknown
            reference != null -> OpenApiType.Reference(reference)
            type == ARRAY -> OpenApiType.ListOf(typeOf(schema.entry("items")?.value))
            type != null && type in scalarTypes -> OpenApiType.Scalar(type, schema.text("format"))
            else -> OpenApiType.Unknown
        }
    }

    private fun schemaNameOf(reference: String): String? = schemaReferences.firstOrNull { reference.startsWith(it) }
        ?.let { reference.removePrefix(it).replace("~1", "/").replace("~0", "~") }
        ?.takeIf { it.isNotEmpty() }
}
