package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.GraphStore
import io.github.krosstack.kscope.spi.graph.StoredUnit
import io.github.krosstack.kscope.spi.search.IndexedDocument
import io.github.krosstack.kscope.spi.search.SearchContent
import io.github.krosstack.kscope.spi.search.SearchIndex
import io.github.krosstack.kscope.store.read.BatchReader
import io.github.krosstack.kscope.store.read.SqliteGraphReader
import io.github.krosstack.kscope.store.read.UnitCatalog
import io.github.krosstack.kscope.store.search.SearchDump
import io.github.krosstack.kscope.store.search.SearchIndexWriter
import io.github.krosstack.kscope.store.search.SqliteSearchIndex
import io.github.krosstack.kscope.store.sqlite.Database
import io.github.krosstack.kscope.store.sqlite.StoreOpener
import io.github.krosstack.kscope.store.structure.StructureReader
import io.github.krosstack.kscope.store.table.FactWriter
import java.nio.file.Path

/** A graph store on one SQLite connection. It is not thread-safe. */
public class SqliteGraphStore internal constructor(private val database: Database) :
    GraphStore,
    GraphReader by SqliteGraphReader(database) {
    private val catalog = UnitCatalog(database)
    private val writer = FactWriter(database)
    private val batchReader = BatchReader(database, catalog)
    private val search = SqliteSearchIndex(database)
    private val searchDump = SearchDump(database)
    private val searchWriter = SearchIndexWriter(database, StructureReader(database))
    private val countReader = CountReader(database)
    internal val readDataVersion: () -> Long = database::dataVersion

    override fun units(): List<StoredUnit> = catalog.units()

    override fun replace(batch: FactBatch) {
        writer.requireUniqueSymbols(batch)
        database.transaction {
            catalog.row(batch.producer, batch.owner)?.let { searchWriter.markSymbolsOf(it.id) }
            val unit = catalog.recreate(batch)
            writer.write(unit, batch.facts)
            searchWriter.markSymbolsOf(unit)
        }
    }

    override fun remove(producer: ProducerRef, owner: OwnershipUnit) {
        database.transaction {
            catalog.row(producer, owner)?.let { searchWriter.markSymbolsOf(it.id) }
            catalog.remove(producer, owner)
        }
    }

    override fun <T> transaction(block: () -> T): T = database.transaction(block)

    override fun batches(): List<FactBatch> = batchReader.batches()

    override fun batch(producer: ProducerRef, owner: OwnershipUnit): FactBatch? = batchReader.batch(producer, owner)

    override fun searchIndex(): SearchIndex = search

    override fun refreshSearchIndex(content: SearchContent) {
        searchWriter.refresh(content)
    }

    override fun searchDocuments(): List<IndexedDocument> = searchDump.documents()

    /** Counts the structure and the diagnostics with the database, so that the facts stay on disk. */
    public fun counts(): StoreCounts = countReader.counts()

    override fun close() {
        database.close()
    }

    public companion object {
        /** Creates the file and its parent directories when missing. */
        public fun open(databaseFile: Path): StoreOpenResult = StoreOpener.open(databaseFile)

        public fun inMemory(): GraphStore = StoreOpener.inMemory()
    }
}
