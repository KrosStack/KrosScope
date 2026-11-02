package io.github.krosstack.kscope.store.search

import io.github.krosstack.kscope.store.sqlite.Database

/** What an artifact document was built from: the hash of its content, and whether its text was indexed. */
internal class ContentStamp(val hash: String, val hasContent: Boolean)

/** A document to add to the index: the key of its node and the text of each column. */
internal class TextDocument(val key: String, val fields: Map<String, String>, val stamp: ContentStamp? = null)

/** Writes the documents of one set of the index: a row in the full-text table and one in the table of keys. */
internal class DocumentTable(private val database: Database, private val layout: SearchLayout) {
    fun deleteByKeys(keys: List<String>) {
        if (keys.isEmpty()) return
        val marks = placeholders(keys.size)
        database.update(
            "DELETE FROM ${layout.fts} WHERE rowid IN (SELECT doc FROM ${layout.documents} WHERE node_key IN ($marks))",
            keys,
        )
        database.update("DELETE FROM ${layout.documents} WHERE node_key IN ($marks)", keys)
    }

    fun deleteByDocs(docs: List<Long>) {
        if (docs.isEmpty()) return
        val marks = placeholders(docs.size)
        database.update("DELETE FROM ${layout.fts} WHERE rowid IN ($marks)", docs)
        database.update("DELETE FROM ${layout.documents} WHERE doc IN ($marks)", docs)
    }

    /** Numbers the documents after the highest number in use, in the order given. */
    fun insert(documents: List<TextDocument>) {
        if (documents.isEmpty()) return
        val first = nextDoc()
        val numbered = documents.mapIndexed { index, document -> (first + index) to document }
        database.insertAll(documentSql(), numbered, INSERT_BATCH) { binder, (doc, document) ->
            binder.long(doc)
            binder.text(document.key)
            document.stamp?.let {
                binder.text(it.hash)
                binder.int(if (it.hasContent) 1 else 0)
            }
        }
        database.insertAll(textSql(), numbered, INSERT_BATCH) { binder, (doc, document) ->
            binder.long(doc)
            layout.columns.forEach { binder.text(document.fields.getValue(it)) }
        }
    }

    private fun documentSql(): String {
        val columns = listOf("doc", "node_key") + layout.stampColumns
        return "INSERT INTO ${layout.documents} (${columns.joinToString(", ")}) VALUES (${placeholders(columns.size)})"
    }

    private fun textSql(): String {
        val columns = listOf("rowid") + layout.columns
        return "INSERT INTO ${layout.fts} (${columns.joinToString(", ")}) VALUES (${placeholders(columns.size)})"
    }

    private fun nextDoc(): Long =
        database.query("SELECT COALESCE(MAX(doc), 0) + 1 FROM ${layout.documents}") { it.long() }.single()

    private fun placeholders(count: Int) = List(count) { "?" }.joinToString(", ")

    private companion object {
        const val INSERT_BATCH = 64
    }
}
