package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import io.github.krosstack.kscope.store.codec.Wires
import io.github.krosstack.kscope.store.sqlite.Database
import io.github.krosstack.kscope.store.sqlite.WhereClause
import io.github.krosstack.kscope.store.table.SymbolTable
import io.github.krosstack.kscope.store.table.select
import kotlinx.serialization.encodeToString

internal class SymbolReader(private val database: Database) {
    fun symbol(id: SymbolId): Symbol? =
        PreferredSymbol.among(database.select(SymbolTable, WhereClause().equal("id", id.value)))

    fun symbols(query: SymbolQuery): List<Symbol> {
        val onePerId = database.select(SymbolTable, whereFor(query)).groupBy { it.id }.values
            .mapNotNull(PreferredSymbol::among)
        return CanonicalOrder.sorted(onePerId, SymbolTable::wrap).take(query.limit)
    }

    private fun whereFor(query: SymbolQuery): WhereClause {
        val where = WhereClause()
            .equal("name", query.name)
            .equal("qualified_name", query.qualifiedName)
            .equal("kind_id", query.kindId)
            .equal("kind_category", query.category?.let(Wires.symbolCategory::nameOf))
            .equal("declaration_repository", query.artifact?.repository?.value)
            .equal("declaration_path", query.artifact?.path?.value)
            .equal("container", query.container?.value)
        query.flag?.let { where.containsText("flags", KscopeJson.encodeToString<SymbolFlag>(it)) }
        return where
    }
}
