package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.store.sqlite.Schema
import java.sql.Connection

private const val UNSET = 0

internal object IndexFileState {
    fun of(connection: Connection): IndexState {
        val version = singleInt(connection, "PRAGMA user_version")
        return when {
            version != Schema.VERSION -> otherThanCurrent(connection, version)
            anyArtifact(connection) -> IndexState.Readable
            else -> IndexState.Absent
        }
    }

    private fun otherThanCurrent(connection: Connection, version: Int): IndexState =
        if (version == UNSET && !hasTables(connection)) {
            IndexState.Absent
        } else {
            IndexState.OtherFormat(version, Schema.VERSION)
        }

    private fun hasTables(connection: Connection): Boolean =
        singleInt(connection, "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table'") > 0

    private fun anyArtifact(connection: Connection): Boolean = connection.createStatement().use { statement ->
        statement.executeQuery("SELECT 1 FROM artifact LIMIT 1").use { it.next() }
    }

    private fun singleInt(connection: Connection, sql: String): Int = connection.createStatement().use { statement ->
        statement.executeQuery(sql).use { rows ->
            check(rows.next()) { "The query returned no row: $sql" }
            rows.getInt(1)
        }
    }
}
