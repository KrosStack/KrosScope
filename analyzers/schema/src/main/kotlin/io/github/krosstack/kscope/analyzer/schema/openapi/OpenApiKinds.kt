package io.github.krosstack.kscope.analyzer.schema.openapi

import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolKind

/** The kinds and attributes of the symbols of an OpenAPI document, whose operations the linkers read. */
internal object OpenApiKinds {
    const val OPERATION_KIND = "openapi.operation"
    const val VERSION_ATTRIBUTE = "openapi.version"
    const val SERVERS_ATTRIBUTE = "openapi.servers"
    const val METHOD_ATTRIBUTE = "openapi.method"
    const val PATH_ATTRIBUTE = "openapi.path"
    const val OPERATION_ID_ATTRIBUTE = "openapi.operation-id"

    val document = SymbolKind("openapi.document", SymbolCategory.SCHEMA)
    val operation = SymbolKind(OPERATION_KIND, SymbolCategory.CONTRACT)
    val schema = SymbolKind("openapi.schema", SymbolCategory.SCHEMA)
}
