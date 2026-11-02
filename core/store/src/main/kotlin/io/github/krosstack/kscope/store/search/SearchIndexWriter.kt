package io.github.krosstack.kscope.store.search

import io.github.krosstack.kscope.spi.search.SearchContent
import io.github.krosstack.kscope.store.sqlite.Database
import io.github.krosstack.kscope.store.structure.StructureReader

/** Keeps the search index in step with the facts. Every method runs inside the caller's transaction. */
internal class SearchIndexWriter(private val database: Database, structure: StructureReader) {
    private val symbols = SymbolIndexer(database)
    private val artifacts = ArtifactIndexer(database, structure)

    fun markSymbolsOf(unit: Long) {
        database.update(
            "INSERT OR IGNORE INTO ${SymbolIndexer.DIRTY} (node_key) SELECT id FROM symbol WHERE unit = ?",
            listOf(unit),
        )
    }

    fun refresh(content: SearchContent) {
        database.transaction {
            symbols.refresh()
            artifacts.refresh(content)
        }
    }
}
