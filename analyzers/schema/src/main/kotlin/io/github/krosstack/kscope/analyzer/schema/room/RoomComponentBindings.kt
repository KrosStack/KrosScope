package io.github.krosstack.kscope.analyzer.schema.room

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.graph.GraphReader

private val usageKinds = setOf(RelationKind.REFERENCES, RelationKind.TYPE_USES)
private val sqlTable = Regex("""\b(?:FROM|JOIN|INTO|UPDATE)\s+(?:[`"]?)([A-Za-z_]\w*)""", RegexOption.IGNORE_CASE)

/** The relationships of a Room database to its entities and DAOs, and of a DAO to the entities it works on. */
internal class RoomComponentBindings(private val graph: GraphReader, private val classes: RoomClasses) {
    private val entityIds = classes.entities.map { it.id }.toSet()
    private val daoIds = classes.daos.map { it.id }.toSet()

    /** The entities a database lists in its annotation, found from the references the compiler resolved. */
    fun entitiesOf(database: Symbol): List<Symbol> =
        usesFrom(database).mapNotNull { (_, target) -> classes.entities.firstOrNull { it.id == target } }.distinct()

    fun all(): List<Relationship> = classes.databases.flatMap(::databaseBindings) + classes.daos.flatMap(::daoBindings)

    private fun databaseBindings(database: Symbol): List<Relationship> {
        val entities = usesFrom(database).filter { (_, target) -> target in entityIds }
            .map { (use, _) -> bind(database, use, "entity") }
        val daos = classes.membersOf(database).flatMap(::usesFrom).filter { (_, target) -> target in daoIds }
            .map { (use, _) -> bind(database, use, "dao") }
        return entities + daos
    }

    private fun daoBindings(dao: Symbol): List<Relationship> {
        val members = classes.membersOf(dao)
        val entityUses = members.flatMap(::usesFrom).filter { (_, target) -> target in entityIds }
            .map { (use, _) -> bind(dao, use, "entity") }
        return entityUses + members.flatMap(::queryTables)
    }

    // The compiler-resolved uses of a symbol, with the symbol that each one targets.
    private fun usesFrom(symbol: Symbol): List<Pair<Relationship, SymbolId>> =
        graph.outgoing(NodeRef.OfSymbol(symbol.id), usageKinds).mapNotNull { use ->
            val target = ((use.resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol)?.id
            target?.let { use to it }
        }

    private fun bind(from: Symbol, use: Relationship, subKind: String) = Relationship(
        from = NodeRef.OfSymbol(from.id),
        kind = RelationKind.BINDS,
        subKind = subKind,
        resolution = use.resolution,
        evidenceClass = use.evidenceClass,
        site = use.site,
        scope = use.scope,
    )

    // The tables that the SQL of a `@Query` names are matched with the tables of the entities by name only.
    private fun queryTables(method: Symbol): List<Relationship> {
        val sql = method.attributes["annotation.Query.value"] ?: return emptyList()
        val names = sqlTable.findAll(sql).map { it.groupValues[1].lowercase() }.toSet()
        return classes.entities.filter { classes.tableOf(it).lowercase() in names }.map { entity ->
            Relationship(
                from = NodeRef.OfSymbol(method.id),
                kind = RelationKind.BINDS,
                subKind = "query-table",
                resolution = Resolution.Resolved(NodeRef.OfSymbol(entity.id), Certainty.HEURISTIC),
                evidenceClass = EvidenceClass.TEXTUAL,
                site = method.nameLocation,
                scope = method.scope,
            )
        }
    }
}
