package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.store.SqliteGraphStore
import java.nio.file.Files
import java.nio.file.Path

/** The files that make up an index directory. */
internal class IndexFiles(private val directory: Path) {
    val database: Path = directory.resolve(DATABASE_NAME)
    val syntheticSources: Path = directory.resolve("synthetic-sources.json")
    val apiDigests: Path = directory.resolve("api-digests.json")

    /**
     * Creates the directory of a new index with a `.gitignore` that ignores everything in it, itself included, so that
     * a project needs no entry of its own. A directory that exists is left as it is: it may be the user's.
     */
    fun createDirectory() {
        if (Files.isDirectory(directory)) return
        Files.createDirectories(directory)
        Files.writeString(directory.resolve(IGNORE_FILE_NAME), IGNORE_FILE_CONTENT)
    }

    fun deleteDerivedData() {
        listOf(database, wal(), shm(), syntheticSources, apiDigests).forEach(Files::deleteIfExists)
    }

    private fun wal() = directory.resolve("$DATABASE_NAME-wal")

    private fun shm() = directory.resolve("$DATABASE_NAME-shm")

    private companion object {
        const val DATABASE_NAME = "index.db"
        const val IGNORE_FILE_NAME = ".gitignore"
        const val IGNORE_FILE_CONTENT = "# The kscope index is derived data and is rebuilt by a scan.\n*\n"
    }
}

/** An opened store, and the notice to report when an incompatible index had to be discarded. */
internal class OpenedIndex(val store: SqliteGraphStore, val notices: List<Diagnostic>)
