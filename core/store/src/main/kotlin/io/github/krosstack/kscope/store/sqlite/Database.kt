package io.github.krosstack.kscope.store.sqlite

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Savepoint
import java.sql.Statement

internal class Database(private val connection: Connection) : AutoCloseable {
    private var transactionDepth = 0

    fun <T> transaction(block: () -> T): T {
        if (transactionDepth == 0) connection.commit()
        val savepoint = if (transactionDepth > 0) connection.setSavepoint() else null
        transactionDepth++
        var completed = false
        try {
            val result = block()
            completed = true
            return result
        } finally {
            transactionDepth--
            finish(savepoint, completed)
        }
    }

    fun <T> query(sql: String, parameters: List<Any> = emptyList(), read: (Cursor) -> T): List<T> =
        connection.prepareStatement(sql).use { statement ->
            statement.bindAll(parameters)
            statement.executeQuery().use { rows ->
                val cursor = Cursor(rows)
                buildList {
                    while (rows.next()) {
                        cursor.startRow()
                        add(read(cursor))
                    }
                }
            }
        }

    fun update(sql: String, parameters: List<Any> = emptyList()): Int =
        connection.prepareStatement(sql).use { statement ->
            statement.bindAll(parameters)
            statement.executeUpdate()
        }

    fun insertReturningKey(sql: String, parameters: List<Any>): Long =
        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { statement ->
            statement.bindAll(parameters)
            statement.executeUpdate()
            statement.generatedKeys.use { keys ->
                check(keys.next()) { "The insert returned no key" }
                keys.getLong(1)
            }
        }

    fun <T> insertAll(sql: String, values: List<T>, batchSize: Int = BATCH_SIZE, bind: (Binder, T) -> Unit) {
        connection.prepareStatement(sql).use { statement ->
            val binder = Binder(statement)
            values.chunked(batchSize).forEach { chunk ->
                chunk.forEach { value ->
                    binder.startRow()
                    bind(binder, value)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }

    override fun close() = connection.close()

    fun dataVersion(): Long {
        check(transactionDepth == 0) { "Data version must be checked outside a transaction" }
        connection.commit()
        return connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA data_version").use { rows ->
                check(rows.next()) { "SQLite returned no data version" }
                rows.getLong(1)
            }
        }
    }

    private fun finish(savepoint: Savepoint?, completed: Boolean) {
        if (savepoint == null) finishOutermost(completed) else finishNested(savepoint, completed)
    }

    private fun finishOutermost(completed: Boolean) {
        if (completed) connection.commit() else connection.rollback()
    }

    private fun finishNested(savepoint: Savepoint, completed: Boolean) {
        if (!completed) connection.rollback(savepoint)
        connection.releaseSavepoint(savepoint)
    }

    private companion object {
        const val BATCH_SIZE = 5_000
    }
}

private fun PreparedStatement.bindAll(parameters: List<Any>) {
    parameters.forEachIndexed { index, value -> setObject(index + 1, value) }
}
