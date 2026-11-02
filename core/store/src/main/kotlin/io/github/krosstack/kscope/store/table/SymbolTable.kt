package io.github.krosstack.kscope.store.table

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.store.codec.Wires
import io.github.krosstack.kscope.store.codec.attributes
import io.github.krosstack.kscope.store.codec.enum
import io.github.krosstack.kscope.store.codec.enumOrNull
import io.github.krosstack.kscope.store.codec.json
import io.github.krosstack.kscope.store.codec.location
import io.github.krosstack.kscope.store.codec.locationColumns
import io.github.krosstack.kscope.store.codec.locationOrNull
import io.github.krosstack.kscope.store.codec.nodeRefColumns
import io.github.krosstack.kscope.store.codec.nodeRefOrNull
import io.github.krosstack.kscope.store.codec.scope
import io.github.krosstack.kscope.store.codec.scopeColumns
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor
import io.github.krosstack.kscope.store.sqlite.IndexSpec
import io.github.krosstack.kscope.store.sqlite.TableSpec
import io.github.krosstack.kscope.store.sqlite.indexOn

internal object SymbolTable : FactTable<Symbol> {
    override val spec = TableSpec(
        name = "symbol",
        columns = listOf(
            "id TEXT NOT NULL",
            "kind_id TEXT NOT NULL",
            "kind_category TEXT NOT NULL",
            "name TEXT NOT NULL",
            "qualified_name TEXT",
            "signature TEXT",
            "container TEXT",
            "visibility TEXT",
            "flags TEXT NOT NULL",
        ) + originColumns() +
            locationColumns("declaration") +
            locationColumns("name_location") +
            "documentation TEXT" +
            scopeColumns() +
            listOf("evidence_class TEXT NOT NULL", "certainty TEXT NOT NULL", "attributes TEXT NOT NULL"),
        indexes = listOf(
            IndexSpec(listOf("unit", "id"), unique = true),
            indexOn("id"),
            indexOn("name"),
            indexOn("qualified_name"),
            indexOn("kind_id"),
            indexOn("container"),
            indexOn("declaration_repository", "declaration_path"),
        ),
    )

    override fun bind(binder: Binder, value: Symbol) = with(binder) {
        text(value.id.value)
        text(value.kind.id)
        enum(Wires.symbolCategory, value.kind.category)
        text(value.name)
        textOrNull(value.qualifiedName)
        textOrNull(value.signature)
        textOrNull(value.container?.value)
        enumOrNull(Wires.visibility, value.visibility)
        json(value.flags.sorted())
        origin(value.origin)
        location(value.declaration)
        location(value.nameLocation)
        textOrNull(value.documentation)
        scope(value.scope)
        enum(Wires.evidenceClass, value.evidenceClass)
        enum(Wires.certainty, value.certainty)
        attributes(value.attributes)
    }

    override fun read(cursor: Cursor): Symbol = with(cursor) {
        Symbol(
            id = SymbolId(text()),
            kind = SymbolKind(text(), enum(Wires.symbolCategory)),
            name = text(),
            qualifiedName = textOrNull(),
            signature = textOrNull(),
            container = textOrNull()?.let(::SymbolId),
            visibility = enumOrNull(Wires.visibility),
            flags = json(),
            origin = origin(),
            declaration = locationOrNull(),
            nameLocation = locationOrNull(),
            documentation = textOrNull(),
            scope = scope(),
            evidenceClass = enum(Wires.evidenceClass),
            certainty = enum(Wires.certainty),
            attributes = attributes(),
        )
    }

    override fun wrap(value: Symbol): Fact = Fact.OfSymbol(value)

    override fun unwrap(fact: Fact): Symbol? = (fact as? Fact.OfSymbol)?.value
}

private object Origins {
    const val SOURCE = "source"
    const val GENERATED = "generated"
    const val SYNTHETIC = "synthetic"
    const val LIBRARY = "library"
}

private fun originColumns(): List<String> =
    listOf("origin TEXT NOT NULL", "origin_value TEXT") + nodeRefColumns("origin_source", required = false)

private fun Binder.origin(origin: SymbolOrigin) {
    when (origin) {
        is SymbolOrigin.Source -> originParts(Origins.SOURCE, null, null)
        is SymbolOrigin.Generated -> originParts(Origins.GENERATED, origin.generator, origin.sourceOfTruth)
        is SymbolOrigin.Synthetic -> originParts(Origins.SYNTHETIC, origin.plugin, null)
        is SymbolOrigin.Library -> originParts(Origins.LIBRARY, origin.coordinate, null)
    }
}

private fun Binder.originParts(kind: String, value: String?, sourceOfTruth: NodeRef?) {
    text(kind)
    textOrNull(value)
    nodeRefOrNull(sourceOfTruth)
}

private fun Cursor.origin(): SymbolOrigin {
    val kind = text()
    val value = textOrNull()
    val sourceOfTruth = nodeRefOrNull()
    return when (kind) {
        Origins.SOURCE -> SymbolOrigin.Source
        Origins.GENERATED -> SymbolOrigin.Generated(checkNotNull(value), sourceOfTruth)
        Origins.SYNTHETIC -> SymbolOrigin.Synthetic(checkNotNull(value))
        Origins.LIBRARY -> SymbolOrigin.Library(value)
        else -> error("Unknown stored symbol origin '$kind'")
    }
}
