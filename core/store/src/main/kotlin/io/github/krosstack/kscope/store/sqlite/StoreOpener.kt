package io.github.krosstack.kscope.store.sqlite

import io.github.krosstack.kscope.spi.graph.GraphStore
import io.github.krosstack.kscope.store.SqliteGraphStore
import io.github.krosstack.kscope.store.StoreOpenResult
import org.sqlite.SQLiteConfig
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

internal object StoreOpener {
    private const val IN_MEMORY_URL = "jdbc:sqlite::memory:"

    fun open(databaseFile: Path): StoreOpenResult {
        val absoluteFile = databaseFile.toAbsolutePath()
        absoluteFile.parent?.let(Files::createDirectories)
        return open("jdbc:sqlite:$absoluteFile", journaling = true)
    }

    fun inMemory(): GraphStore = when (val result = open(IN_MEMORY_URL, journaling = false)) {
        is StoreOpenResult.Opened -> result.store
        is StoreOpenResult.Incompatible -> error("A new in-memory database cannot be incompatible")
    }

    private fun open(url: String, journaling: Boolean): StoreOpenResult {
        val config = SQLiteConfig().apply { enforceForeignKeys(true) }
        val connection = DriverManager.getConnection(url, config.toProperties())
        var handedOver = false
        try {
            val result = openWithSchema(connection, journaling)
            handedOver = result is StoreOpenResult.Opened
            return result
        } finally {
            if (!handedOver) connection.close()
        }
    }

    private fun openWithSchema(connection: Connection, journaling: Boolean): StoreOpenResult {
        val version = userVersion(connection)
        val isEmpty = !hasTables(connection)
        if (!isEmpty && version != Schema.VERSION) return StoreOpenResult.Incompatible(version, Schema.VERSION)
        configure(connection, journaling)
        val database = Database(connection)
        if (isEmpty) createSchema(database, connection)
        return StoreOpenResult.Opened(SqliteGraphStore(database))
    }

    private fun configure(connection: Connection, journaling: Boolean) {
        connection.createStatement().use { statement ->
            if (journaling) statement.execute("PRAGMA journal_mode = WAL")
            statement.execute("PRAGMA synchronous = NORMAL")
        }
        connection.autoCommit = false
    }

    private fun createSchema(database: Database, connection: Connection) {
        database.transaction {
            Schema.creationStatements.forEach { database.update(it) }
            connection.createStatement().use { it.execute("PRAGMA user_version = ${Schema.VERSION}") }
        }
    }

    private fun userVersion(connection: Connection): Int = singleInt(connection, "PRAGMA user_version")

    private fun hasTables(connection: Connection): Boolean =
        singleInt(connection, "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table'") > 0

    private fun singleInt(connection: Connection, sql: String): Int = connection.createStatement().use { statement ->
        statement.executeQuery(sql).use { rows ->
            check(rows.next()) { "The query returned no row: $sql" }
            rows.getInt(1)
        }
    }
}
