package io.github.krosstack.kscope.analyzer.schema.openapi

import io.github.krosstack.kscope.analyzer.schema.Span
import io.github.krosstack.kscope.analyzer.schema.document.DocEntry

/**
 * The type of a schema as far as a client generated from the document needs it: a [Scalar] of the types `string`,
 * `integer`, `number` and `boolean` with its format, a list, a reference to a schema of the document by name, or
 * [Unknown] for anything else.
 */
internal sealed interface OpenApiType {
    data class Scalar(val type: String, val format: String?) : OpenApiType

    data class ListOf(val items: OpenApiType) : OpenApiType

    data class Reference(val schema: String) : OpenApiType

    data object Unknown : OpenApiType
}

/** A parameter of an operation, given in the `path`, the `query` or a `header`, with its type. */
internal data class OpenApiParameter(val name: String, val place: String, val required: Boolean, val type: OpenApiType)

/** The body of an operation in JSON. */
internal data class OpenApiBody(val type: OpenApiType, val required: Boolean)

/** A property of an object schema and whether the schema requires it. */
internal data class OpenApiProperty(val name: String, val required: Boolean, val type: OpenApiType)

/**
 * An operation of a path: the [method] in upper case, the [path] as written, and where it and its name lie. A client
 * needs more of it: its [tags], its [parameters], its JSON [body], and the type of the first successful response with
 * a JSON schema, which is null when there is none.
 */
internal data class OpenApiOperation(
    val method: String,
    val path: String,
    val operationId: String?,
    val summary: String?,
    val declaration: Span,
    val nameSpan: Span,
    val tags: List<String> = emptyList(),
    val parameters: List<OpenApiParameter> = emptyList(),
    val body: OpenApiBody? = null,
    val response: OpenApiType? = null,
)

/**
 * An entry of `components/schemas`, or of `definitions` in a Swagger 2 document, as the [section] says. [properties]
 * are those of a plain object schema, with no enumeration and no composition; any other schema has none.
 */
internal class OpenApiSchema(
    val name: String,
    val section: String,
    val declaration: Span,
    val nameSpan: Span,
    val properties: List<OpenApiProperty> = emptyList(),
)

/**
 * What the analysis keeps of an OpenAPI document: the entry that [version] declares it with, its [title], the paths of
 * its [servers] without a trailing slash, its operations and its schemas.
 */
internal class OpenApiDocument(
    val version: DocEntry,
    val title: String?,
    val servers: List<String>,
    val operations: List<OpenApiOperation>,
    val schemas: List<OpenApiSchema>,
)
