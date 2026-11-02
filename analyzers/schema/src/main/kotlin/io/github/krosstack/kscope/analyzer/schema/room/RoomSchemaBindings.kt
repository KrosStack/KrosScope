package io.github.krosstack.kscope.analyzer.schema.room

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

/**
 * Joins the exported schema of a database to the code. Of the versions that were exported, the latest counts, for each
 * module on its own. A schema entity is bound to the `@Entity` class of its table, and its fields to the properties of
 * that class.
 */
internal class RoomSchemaBindings(
    private val graph: GraphReader,
    private val classes: RoomClasses,
    private val components: RoomComponentBindings,
) {
    private val schemaEntities: List<Symbol> = latestVersions(
        graph.symbols(SymbolQuery(kindId = RoomKinds.entity.id, limit = Int.MAX_VALUE)),
    )

    fun all(): List<Relationship> =
        schemaEntities.flatMap(::entityBindings) + classes.databases.mapNotNull(::exportedSchema)

    private fun entityBindings(schemaEntity: Symbol): List<Relationship> {
        val entity = classFor(schemaEntity) ?: return emptyList()
        val certainty = if (entity.second) Certainty.EXACT else Certainty.PROBABLE
        val isExplicit = classes.hasExplicitTable(entity.first)
        val evidence = if (isExplicit) EvidenceClass.GENERATED_ARTIFACT else EvidenceClass.CONVENTION
        val own = bind(schemaEntity, entity.first, "schema-entity", certainty, evidence)
        val properties = classes.propertiesOf(entity.first)
        val schemaFields = graph.symbols(SymbolQuery(container = schemaEntity.id, limit = Int.MAX_VALUE))
        val fields = schemaFields.mapNotNull { field ->
            propertyFor(field, properties)?.let {
                bind(field, it, "schema-field", Certainty.EXACT, EvidenceClass.GENERATED_ARTIFACT)
            }
        }
        return listOf(own) + fields
    }

    // The class of a table, and whether it is the only one that fits: of several, the ones its database lists win,
    // and where it lists none the ones of the module of the schema.
    private fun classFor(schemaEntity: Symbol): Pair<Symbol, Boolean>? {
        val module = RoomModules.of(schemaEntity)
        val named = classes.entities.filter { classes.tableOf(it) == schemaEntity.name }
        val databaseName = schemaEntity.attributes["room.schema.database"]
        val database = RoomModules.prefer(classes.databases.filter { it.qualifiedName == databaseName }, module)
            .firstOrNull()
        val listed = database?.let { components.entitiesOf(it) }.orEmpty()
        val candidates = named.filter { it in listed }.ifEmpty { RoomModules.prefer(named, module) }
            .sortedBy { it.id.value }
        return candidates.firstOrNull()?.let { it to (candidates.size == 1) }
    }

    private fun propertyFor(field: Symbol, properties: List<Symbol>): Symbol? {
        val column = field.name
        val path = field.attributes["room.schema.field-path"]
        return properties.firstOrNull { it.attributes["annotation.ColumnInfo.name"] == column }
            ?: properties.firstOrNull { it.name == path }
            ?: properties.firstOrNull { it.name == column }
    }

    private fun exportedSchema(database: Symbol): Relationship? {
        val exports = schemaEntities.filter { it.attributes["room.schema.database"] == database.qualifiedName }
        val schema = RoomModules.prefer(exports, RoomModules.of(database)).firstOrNull()
        val artifact = schema?.declaration?.artifact ?: return null
        return Relationship(
            from = NodeRef.OfSymbol(database.id),
            kind = RelationKind.BINDS,
            subKind = "exported-schema",
            resolution = Resolution.Resolved(NodeRef.OfArtifact(artifact), Certainty.EXACT),
            evidenceClass = EvidenceClass.GENERATED_ARTIFACT,
            site = null,
            scope = database.scope,
        )
    }

    private fun bind(
        from: Symbol,
        to: Symbol,
        subKind: String,
        certainty: Certainty,
        evidence: EvidenceClass,
    ): Relationship = Relationship(
        from = NodeRef.OfSymbol(from.id),
        kind = RelationKind.BINDS,
        subKind = subKind,
        resolution = Resolution.Resolved(NodeRef.OfSymbol(to.id), certainty),
        evidenceClass = evidence,
        site = from.nameLocation,
        scope = from.scope,
    )

    private fun latestVersions(entities: List<Symbol>): List<Symbol> = entities
        .groupBy { it.attributes["room.schema.database"] to RoomModules.of(it) }
        .values.flatMap { versions ->
            val latest = versions.maxOf { it.attributes["room.schema.version"]?.toIntOrNull() ?: 0 }
            versions.filter { (it.attributes["room.schema.version"]?.toIntOrNull() ?: 0) == latest }
        }
}
