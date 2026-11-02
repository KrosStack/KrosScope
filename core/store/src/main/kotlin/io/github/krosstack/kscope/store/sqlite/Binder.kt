package io.github.krosstack.kscope.store.sqlite

import java.sql.PreparedStatement
import java.sql.Types

internal class Binder(private val statement: PreparedStatement) {
    private var next = 1

    fun startRow() {
        next = 1
    }

    fun long(value: Long) = statement.setLong(next++, value)

    fun int(value: Int) = statement.setInt(next++, value)

    fun double(value: Double) = statement.setDouble(next++, value)

    fun text(value: String) = statement.setString(next++, value)

    fun textOrNull(value: String?) {
        if (value == null) statement.setNull(next++, Types.VARCHAR) else text(value)
    }

    fun intOrNull(value: Int?) {
        if (value == null) statement.setNull(next++, Types.INTEGER) else int(value)
    }
}
