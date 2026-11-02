package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.store.sqlite.Schema
import org.sqlite.SQLiteConfig
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/** Looks into an index file without creating, migrating or discarding anything. */
public object IndexProbe {
    /** Whether [databaseFile] holds an index of the current schema version with at least one artifact. */
    public fun hasArtifacts(databaseFile: Path): Boolean {
        if (!Files.isRegularFile(databaseFile)) return false
        val config = SQLiteConfig().apply { setReadOnly(true) }
        return try {
            DriverManager.getConnection("jdbc:sqlite:${databaseFile.toAbsolutePath()}", config.toProperties())
                .use(::holdsArtifacts)
        } catch (_: SQLException) {
            false
        }
    }

    private fun holdsArtifacts(connection: Connection): Boolean = current(connection) && anyArtifact(connection)

    private fun current(connection: Connection): Boolean = connection.createStatement().use { statement ->
        statement.executeQuery("PRAGMA user_version").use { it.next() && it.getInt(1) == Schema.VERSION }
    }

    private fun anyArtifact(connection: Connection): Boolean = connection.createStatement().use { statement ->
        statement.executeQuery("SELECT 1 FROM artifact LIMIT 1").use { it.next() }
    }
}
