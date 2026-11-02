package io.github.krosstack.kscope.analyzer.schema.openapi

import io.github.krosstack.kscope.analyzer.schema.IdentityFactory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId

/**
 * The ids of the symbols of an OpenAPI document: `openapi/<file>:` and then the operation as `<METHOD> <path>.` or the
 * schema as `<name>#`, in the module and source set of the file. A qualified name is the file and the JSON pointer of
 * the element in it, the form that a `$ref` uses.
 */
internal class OpenApiIdentities(private val identity: IdentityFactory, private val file: String) {
    fun documentId(): SymbolId = identity.idOf(base())

    fun operationId(operation: OpenApiOperation): SymbolId =
        identity.idOf(base().term("${operation.method} ${operation.path}"))

    fun schemaId(schema: OpenApiSchema): SymbolId = identity.idOf(base().type(schema.name))

    fun documentName(): String = file

    fun operationName(operation: OpenApiOperation): String =
        "$file#/paths/${pointerSegment(operation.path)}/${operation.method.lowercase()}"

    fun schemaName(schema: OpenApiSchema): String = "$file#/${schema.section}/${pointerSegment(schema.name)}"

    private fun base(): SymbolDescriptor = SymbolDescriptor().namespace(NAMESPACE).meta(file)

    private fun pointerSegment(text: String): String = text.replace("~", "~0").replace("/", "~1")

    private companion object {
        const val NAMESPACE = "openapi"
    }
}
