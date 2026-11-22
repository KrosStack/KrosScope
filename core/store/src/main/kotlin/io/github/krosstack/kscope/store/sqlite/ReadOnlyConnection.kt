package io.github.krosstack.kscope.store.sqlite

import org.sqlite.SQLiteConfig
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

internal object ReadOnlyConnection {
    fun open(databaseFile: Path): Connection {
        val config = SQLiteConfig().apply { setReadOnly(true) }
        val connection = DriverManager.getConnection(
            "jdbc:sqlite:${databaseFile.toAbsolutePath()}",
            config.toProperties(),
        )
        connection.autoCommit = false
        return connection
    }
}
