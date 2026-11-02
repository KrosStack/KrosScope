package sample.tasktide.data.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

actual class DriverFactory(private val file: File? = null) {
    actual fun createDriver(): SqlDriver {
        val url = file?.let { "jdbc:sqlite:${it.path}" } ?: JdbcSqliteDriver.IN_MEMORY
        val driver = JdbcSqliteDriver(url)
        val schema = TaskDatabase.Schema
        val stored = driver.userVersion()
        when {
            stored == 0L -> schema.create(driver)
            stored < schema.version -> schema.migrate(driver, stored, schema.version)
        }
        driver.execute(null, "PRAGMA user_version = ${schema.version}", 0)
        return driver
    }

    private fun SqlDriver.userVersion(): Long = executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
        parameters = 0,
    ).value
}
