package io.github.krosstack.kscope.store.table

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.store.codec.Wires
import io.github.krosstack.kscope.store.codec.attributes
import io.github.krosstack.kscope.store.codec.enum
import io.github.krosstack.kscope.store.codec.location
import io.github.krosstack.kscope.store.codec.locationColumns
import io.github.krosstack.kscope.store.codec.locationOrNull
import io.github.krosstack.kscope.store.codec.nodeRef
import io.github.krosstack.kscope.store.codec.nodeRefColumns
import io.github.krosstack.kscope.store.codec.resolution
import io.github.krosstack.kscope.store.codec.resolutionColumns
import io.github.krosstack.kscope.store.codec.scope
import io.github.krosstack.kscope.store.codec.scopeColumns
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor
import io.github.krosstack.kscope.store.sqlite.TableSpec
import io.github.krosstack.kscope.store.sqlite.indexOn

internal object RelationshipTable : FactTable<Relationship> {
    override val spec = TableSpec(
        name = "relationship",
        columns = nodeRefColumns("from", required = true) +
            listOf("kind TEXT NOT NULL", "sub_kind TEXT") +
            resolutionColumns() +
            "evidence_class TEXT NOT NULL" +
            locationColumns("site") +
            scopeColumns() +
            "attributes TEXT NOT NULL",
        indexes = listOf(
            indexOn("from_kind", "from_key"),
            indexOn("res_target_kind", "res_target_key"),
            indexOn("kind"),
        ),
    )

    override fun bind(binder: Binder, value: Relationship) = with(binder) {
        nodeRef(value.from)
        enum(Wires.relationKind, value.kind)
        textOrNull(value.subKind)
        resolution(value.resolution)
        enum(Wires.evidenceClass, value.evidenceClass)
        location(value.site)
        scope(value.scope)
        attributes(value.attributes)
    }

    override fun read(cursor: Cursor): Relationship = with(cursor) {
        Relationship(
            from = nodeRef(),
            kind = enum(Wires.relationKind),
            subKind = textOrNull(),
            resolution = resolution(),
            evidenceClass = enum(Wires.evidenceClass),
            site = locationOrNull(),
            scope = scope(),
            attributes = attributes(),
        )
    }

    override fun wrap(value: Relationship): Fact = Fact.OfRelationship(value)

    override fun unwrap(fact: Fact): Relationship? = (fact as? Fact.OfRelationship)?.value
}
