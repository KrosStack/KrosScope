package io.github.krosstack.kscope.analyzer.schema.openapi

import io.github.krosstack.kscope.analyzer.schema.SchemaSymbol
import io.github.krosstack.kscope.analyzer.schema.document.DocScalar
import io.github.krosstack.kscope.analyzer.schema.firstSentence

/** The symbols of an OpenAPI document: the document, its operations and its schemas, in the order of the file. */
internal class OpenApiDeclarations(private val document: OpenApiDocument, private val ids: OpenApiIdentities) {
    fun symbols(): List<SchemaSymbol> =
        listOf(documentSymbol()) + document.operations.map(::operationSymbol) + document.schemas.map(::schemaSymbol)

    private fun documentSymbol(): SchemaSymbol {
        val version = (document.version.value as? DocScalar)?.text.orEmpty()
        val servers = document.servers.joinToString(" ").takeIf { it.isNotEmpty() }
        return SchemaSymbol(
            id = ids.documentId(),
            kind = OpenApiKinds.document,
            name = document.title ?: ids.documentName().substringAfterLast('/'),
            qualifiedName = ids.documentName(),
            signature = "${document.version.key} $version".trim(),
            container = null,
            declaration = document.version.span,
            nameSpan = document.version.keySpan,
            attributes = listOfNotNull(
                OpenApiKinds.VERSION_ATTRIBUTE to version,
                servers?.let { OpenApiKinds.SERVERS_ATTRIBUTE to it },
            ).toMap(),
        )
    }

    private fun operationSymbol(operation: OpenApiOperation): SchemaSymbol = SchemaSymbol(
        id = ids.operationId(operation),
        kind = OpenApiKinds.operation,
        name = operation.operationId ?: "${operation.method} ${operation.path}",
        qualifiedName = ids.operationName(operation),
        signature = "${operation.method} ${operation.path}",
        container = ids.documentId(),
        declaration = operation.declaration,
        nameSpan = operation.nameSpan,
        documentation = firstSentence(operation.summary),
        attributes = listOfNotNull(
            OpenApiKinds.METHOD_ATTRIBUTE to operation.method,
            OpenApiKinds.PATH_ATTRIBUTE to operation.path,
            operation.operationId?.let { OpenApiKinds.OPERATION_ID_ATTRIBUTE to it },
        ).toMap(),
    )

    private fun schemaSymbol(schema: OpenApiSchema): SchemaSymbol = SchemaSymbol(
        id = ids.schemaId(schema),
        kind = OpenApiKinds.schema,
        name = schema.name,
        qualifiedName = ids.schemaName(schema),
        signature = "schema ${schema.name}",
        container = ids.documentId(),
        declaration = schema.declaration,
        nameSpan = schema.nameSpan,
    )
}
