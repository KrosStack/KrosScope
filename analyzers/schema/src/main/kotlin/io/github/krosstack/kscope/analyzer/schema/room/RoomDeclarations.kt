package io.github.krosstack.kscope.analyzer.schema.room

import io.github.krosstack.kscope.analyzer.schema.IdentityFactory
import io.github.krosstack.kscope.analyzer.schema.SchemaSymbol
import io.github.krosstack.kscope.analyzer.schema.namespaces
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.SymbolDescriptor

/** The symbols of an exported schema: `room/schema/<database>/v<version>#<table>#<column>.` */
internal class RoomDeclarations(
    private val schema: RoomSchema,
    private val databaseName: String,
    private val identity: IdentityFactory,
) {
    fun symbols(): List<SchemaSymbol> = schema.entities.flatMap(::entitySymbols)

    private fun entitySymbols(entity: RoomEntity): List<SchemaSymbol> {
        val descriptor = SymbolDescriptor().namespace("room").namespace("schema").namespaces(databaseName)
            .type("v${schema.version}").type(entity.tableName)
        val entityId = identity.idOf(descriptor)
        val qualified = "$databaseName.${entity.tableName}"
        val own = SchemaSymbol(
            id = entityId,
            kind = RoomKinds.entity,
            name = entity.tableName,
            qualifiedName = qualified,
            signature = entity.createSql.replace(Regex("\\s+"), " "),
            container = null,
            declaration = entity.span,
            nameSpan = entity.span,
            attributes = mapOf(
                "room.schema.database" to databaseName,
                "room.schema.version" to schema.version.toString(),
            ),
            evidence = EvidenceClass.GENERATED_ARTIFACT,
        )
        val fields = entity.fields.map { field ->
            SchemaSymbol(
                id = identity.idOf(descriptor.term(field.columnName)),
                kind = RoomKinds.field,
                name = field.columnName,
                qualifiedName = "$qualified.${field.columnName}",
                signature = "${field.columnName} ${field.affinity}" + if (field.isNotNull) " NOT NULL" else "",
                container = entityId,
                declaration = field.span,
                nameSpan = field.span,
                attributes = mapOf(
                    "room.schema.field-path" to field.fieldPath,
                    "room.schema.affinity" to field.affinity,
                ),
                evidence = EvidenceClass.GENERATED_ARTIFACT,
            )
        }
        return listOf(own) + fields
    }
}
