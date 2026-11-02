package io.github.krosstack.kscope.store.table

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.store.codec.Wires
import io.github.krosstack.kscope.store.codec.enum
import io.github.krosstack.kscope.store.codec.location
import io.github.krosstack.kscope.store.codec.locationColumns
import io.github.krosstack.kscope.store.codec.locationOrNull
import io.github.krosstack.kscope.store.codec.nodeRef
import io.github.krosstack.kscope.store.codec.nodeRefColumns
import io.github.krosstack.kscope.store.codec.scope
import io.github.krosstack.kscope.store.codec.scopeColumns
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor
import io.github.krosstack.kscope.store.sqlite.TableSpec
import io.github.krosstack.kscope.store.sqlite.indexOn

internal object SymbolicReferenceTable : FactTable<SymbolicReference> {
    override val spec = TableSpec(
        name = "symbolic_reference",
        columns = nodeRefColumns("from", required = true) +
            listOf("kind TEXT NOT NULL", "sub_kind TEXT", "namespace TEXT NOT NULL", "key TEXT NOT NULL") +
            locationColumns("site") +
            scopeColumns() +
            "evidence_class TEXT NOT NULL",
        indexes = listOf(indexOn("namespace")),
    )

    override fun bind(binder: Binder, value: SymbolicReference) = with(binder) {
        nodeRef(value.from)
        enum(Wires.relationKind, value.kind)
        textOrNull(value.subKind)
        text(value.namespace)
        text(value.key)
        location(value.site)
        scope(value.scope)
        enum(Wires.evidenceClass, value.evidenceClass)
    }

    override fun read(cursor: Cursor): SymbolicReference = with(cursor) {
        SymbolicReference(
            from = nodeRef(),
            kind = enum(Wires.relationKind),
            subKind = textOrNull(),
            namespace = text(),
            key = text(),
            site = locationOrNull(),
            scope = scope(),
            evidenceClass = enum(Wires.evidenceClass),
        )
    }

    override fun wrap(value: SymbolicReference): Fact = Fact.OfSymbolicReference(value)

    override fun unwrap(fact: Fact): SymbolicReference? = (fact as? Fact.OfSymbolicReference)?.value
}
