package sample.tasktide.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver = AndroidSqliteDriver(TaskDatabase.Schema, context, DATABASE_NAME)

    private companion object {
        const val DATABASE_NAME = "tasktide.db"
    }
}
