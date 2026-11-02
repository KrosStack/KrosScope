package io.github.krosstack.kscope.store.sqlite

import java.sql.ResultSet

internal class Cursor(private val rows: ResultSet) {
    private var next = 1

    fun startRow() {
        next = 1
    }

    fun long(): Long = rows.getLong(next++)

    fun double(): Double = rows.getDouble(next++)

    fun int(): Int = checkNotNull(intOrNull()) { "Unexpected NULL in an integer column" }

    fun intOrNull(): Int? {
        val value = rows.getInt(next++)
        return if (rows.wasNull()) null else value
    }

    fun text(): String = checkNotNull(textOrNull()) { "Unexpected NULL in a text column" }

    fun textOrNull(): String? = rows.getString(next++)
}
