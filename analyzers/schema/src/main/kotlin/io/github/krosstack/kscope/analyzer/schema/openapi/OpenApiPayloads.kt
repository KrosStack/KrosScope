package io.github.krosstack.kscope.analyzer.schema.openapi

import io.github.krosstack.kscope.analyzer.schema.document.DocMapping
import io.github.krosstack.kscope.analyzer.schema.document.DocNode

private val successfulCode = Regex("2(?:[0-9][0-9]|XX|xx)")

/** Reads what an operation takes and gives in JSON: the schema of its request body and of its successful response. */
internal object OpenApiPayloads {
    fun requestBody(operation: DocMapping): OpenApiBody? {
        val body = operation.mapping("requestBody")
        val schema = jsonSchema(body)
        return if (body == null || schema == null) {
            null
        } else {
            OpenApiBody(OpenApiTypeReader.typeOf(schema), body.text("required") == "true")
        }
    }

    /** The type in the first response with a successful code that has a JSON schema; null when there is none. */
    fun response(operation: DocMapping): OpenApiType? = operation.mapping("responses")?.entries.orEmpty()
        .filter { successfulCode.matches(it.key) }
        .firstNotNullOfOrNull { jsonSchema(it.value as? DocMapping) }
        ?.let { OpenApiTypeReader.typeOf(it) }

    /** The schema under the first JSON media type of the content, or the schema of a Swagger 2 holder. */
    fun jsonSchema(holder: DocMapping?): DocNode? {
        val media = holder?.mapping("content")?.entries.orEmpty()
            .firstOrNull { "json" in it.key.lowercase() }?.value as? DocMapping
        return media?.entry("schema")?.value ?: holder?.entry("schema")?.value
    }
}
