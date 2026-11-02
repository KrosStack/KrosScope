package io.github.krosstack.kscope.store.search

import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.store.read.PreferredSymbol
import io.github.krosstack.kscope.store.sqlite.Database
import io.github.krosstack.kscope.store.table.SymbolTable

/** Rebuilds the documents of the symbols whose ids were marked dirty, from the symbols as the graph shows them now. */
internal class SymbolIndexer(private val database: Database) {
    private val layout = SearchLayouts.symbols
    private val table = DocumentTable(database, layout)

    fun refresh() {
        var ids = dirtyIdsAfter("")
        if (ids.isEmpty()) return
        while (ids.isNotEmpty()) {
            reindex(ids)
            ids = dirtyIdsAfter(ids.last())
        }
        database.update("DELETE FROM $DIRTY")
    }

    private fun dirtyIdsAfter(after: String): List<String> = database.query(
        "SELECT node_key FROM $DIRTY WHERE node_key > ? ORDER BY node_key LIMIT $CHUNK",
        listOf(after),
    ) { it.text() }

    private fun reindex(ids: List<String>) {
        table.deleteByKeys(ids)
        val documents = indexable(ids).map { TextDocument(it.id.value, SearchDocuments.symbol(it)) }
        table.insert(documents)
    }

    private fun indexable(ids: List<String>): List<Symbol> {
        val marks = ids.joinToString(", ") { "?" }
        val candidates = database.query(
            "SELECT ${SymbolTable.spec.selectList} FROM symbol WHERE id IN ($marks)",
            ids,
            SymbolTable::read,
        )
        return candidates.groupBy { it.id }.values.mapNotNull(PreferredSymbol::among)
            .filter(SearchDocuments::isIndexable)
            .sortedBy { it.id.value }
    }

    companion object {
        const val DIRTY = "search_symbol_dirty"
        private const val CHUNK = 2_000
    }
}
