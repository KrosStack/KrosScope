package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.store.codec.NodeRefs
import io.github.krosstack.kscope.store.codec.Wires
import io.github.krosstack.kscope.store.sqlite.Database
import io.github.krosstack.kscope.store.sqlite.WhereClause
import io.github.krosstack.kscope.store.table.RelationshipTable
import io.github.krosstack.kscope.store.table.SymbolicReferenceTable
import io.github.krosstack.kscope.store.table.select

internal class RelationshipReader(private val database: Database) {
    fun outgoing(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> = find(
        WhereClause()
            .equal("from_kind", NodeRefs.kindOf(node))
            .equal("from_key", NodeRefs.keyOf(node))
            .oneOf("kind", kinds.map(Wires.relationKind::nameOf)),
    )

    fun incoming(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> = find(
        WhereClause()
            .equal("res_target_kind", NodeRefs.kindOf(node))
            .equal("res_target_key", NodeRefs.keyOf(node))
            .oneOf("kind", kinds.map(Wires.relationKind::nameOf)),
    )

    fun relationships(query: RelationshipQuery): List<Relationship> = find(
        WhereClause()
            .oneOf("kind", query.kinds.map(Wires.relationKind::nameOf))
            .equal("sub_kind", query.subKind)
            .equal("evidence_class", query.evidenceClass?.let(Wires.evidenceClass::nameOf))
            .nullity("res_target_kind", mustBeNull = query.resolved?.not()),
    ).take(query.limit)

    fun symbolicReferences(namespace: String?): List<SymbolicReference> = CanonicalOrder.sorted(
        database.select(SymbolicReferenceTable, WhereClause().equal("namespace", namespace)),
        SymbolicReferenceTable::wrap,
    )

    private fun find(where: WhereClause): List<Relationship> =
        CanonicalOrder.sorted(database.select(RelationshipTable, where), RelationshipTable::wrap)
}
