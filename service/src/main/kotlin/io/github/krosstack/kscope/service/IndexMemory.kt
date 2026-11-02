package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.indexLevel
import io.github.krosstack.kscope.service.graph.MemoizedGraph
import io.github.krosstack.kscope.service.level.LevelMemory
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.search.SearchIndex
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * What a workspace keeps of its index in memory: the reader of the index with the four bulk reads that queries
 * share, and whether those were read. A scan makes both stale, so it calls [forget].
 */
internal class IndexMemory(
    private val engine: ScanEngine,
    private val progress: ProgressListener,
    private val indexFile: Path,
    private val indexLocation: String,
) {
    private var graph: MemoizedGraph? = null
    private var loaded = false
    private var indexed = false
    val levelMemory = LevelMemory {
        if (engine.hasIndexedFiles()) observed(engine.graph())
        engine.indexLevel()?.let(ScanLevel::of)
    }
    private var observedStore: GraphReader? = null
    private var observedVersion: Long? = null
    private var observedFile: Any? = null

    fun reader(): MemoizedGraph {
        val reader = storeOrNull() ?: throw NotIndexedException(indexLocation)
        return graph?.takeIf { it.source === reader } ?: MemoizedGraph(reader).also { graph = it }
    }

    /** The reader of the index, or null when there is no index to read. */
    fun storeOrNull(): GraphReader? = if (hasIndex()) observed(engine.graph()) else null

    /**
     * Decodes the symbols, relationships and structure of the index once, so that each query after it reads from
     * memory. Every query that needs them calls this first; the step is reported as [QueryStages.LOAD_INDEX].
     */
    fun load() {
        if (loaded) return
        requireIndexed()
        progress.stage(QueryStages.LOAD_INDEX) { reader().preload() }
        loaded = true
    }

    fun searchIndex(): SearchIndex {
        reader()
        return engine.searchIndex()
    }

    /**
     * Fails with [NotIndexedException] unless the workspace has an index with files in it. A query that reads a few
     * rows asks this instead of loading everything.
     */
    fun requireIndexed() {
        if (indexed) return
        if (!engine.hasIndexedFiles()) throw NotIndexedException(indexLocation)
        indexed = true
    }

    /** Counts the times that what was read was forgotten; a caller that sees it change knows the index did. */
    var revision = 0L
        private set

    fun forget() {
        revision++
        graph?.invalidate()
        loaded = false
        indexed = false
        levelMemory.forget()
    }

    /**
     * Forgets what was read when the index changed behind this instance: the index file was deleted or replaced, or
     * another process committed to the database. Returns whether anything was forgotten. What it compares with is
     * recorded when the store is first read, by a query or by a read of the level or the summary: the data version of
     * the database on this connection, and what tells one index file from another at the same path, its file key, or
     * its creation time where the platform has none. A level read while there was no index is forgotten at once, so
     * that an index built since is found.
     */
    fun refresh(): Boolean {
        val known = observedStore
        if (known == null) {
            levelMemory.forget()
            return false
        }
        return when {
            fileIdentity(indexFile) != observedFile -> release()
            engine.graph() !== known -> false
            else -> committedElsewhere()
        }
    }

    private fun observed(store: GraphReader): GraphReader {
        if (observedStore !== store) {
            observedFile = fileIdentity(indexFile)
            observedStore = store
            observedVersion = engine.dataVersion()
        }
        return store
    }

    private fun committedElsewhere(): Boolean {
        val version = engine.dataVersion()
        val changed = version != observedVersion
        if (changed) forget()
        observedVersion = version
        return changed
    }

    private fun release(): Boolean {
        forget()
        engine.close()
        observedStore = null
        observedVersion = null
        observedFile = null
        return true
    }

    // Opening the store replaces an index of another schema version, which only a scan may do, so until the store is
    // open the index is looked into without opening it.
    private fun hasIndex(): Boolean =
        Files.isRegularFile(indexFile) && (observedStore != null || engine.hasIndexedFiles())
}

private fun fileIdentity(file: Path): Any? = try {
    Files.readAttributes(file, BasicFileAttributes::class.java).let { it.fileKey() ?: it.creationTime() }
} catch (_: IOException) {
    null
}
