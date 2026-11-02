package io.github.krosstack.kscope.store.search

import io.github.krosstack.kscope.spi.search.IndexedDocument
import io.github.krosstack.kscope.store.sqlite.Database

/** The documents of the search index in canonical order, for dumps and verify mode. */
internal class SearchDump(private val database: Database) {
    fun documents(): List<IndexedDocument> = SearchLayouts.all.flatMap(::documentsOf)

    private fun documentsOf(layout: SearchLayout): List<IndexedDocument> {
        val columns = layout.columns.joinToString(", ") { "t.$it" }
        return database.query(
            "SELECT d.node_key, $columns FROM ${layout.fts} t JOIN ${layout.documents} d ON d.doc = t.rowid " +
                "ORDER BY d.node_key",
        ) { cursor ->
            val key = cursor.text()
            IndexedDocument(layout.scope, key, layout.columns.associateWith { cursor.text() })
        }
    }
}
