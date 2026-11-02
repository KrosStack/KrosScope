package io.github.krosstack.kscope.store.sqlite

internal class WhereClause {
    private val conditions = mutableListOf<String>()
    private val values = mutableListOf<Any>()

    val parameters: List<Any> get() = values.toList()

    fun equal(column: String, value: String?): WhereClause = apply {
        if (value != null) {
            conditions += "$column = ?"
            values += value
        }
    }

    fun oneOf(column: String, options: Collection<String>): WhereClause = apply {
        if (options.isNotEmpty()) {
            conditions += "$column IN (${options.joinToString(", ") { "?" }})"
            values.addAll(options.sorted())
        }
    }

    fun containsText(column: String, fragment: String): WhereClause = apply {
        conditions += "instr($column, ?) > 0"
        values += fragment
    }

    fun nullity(column: String, mustBeNull: Boolean?): WhereClause = apply {
        if (mustBeNull != null) conditions += "$column IS ${if (mustBeNull) "" else "NOT "}NULL"
    }

    fun toSql(): String = if (conditions.isEmpty()) "" else conditions.joinToString(" AND ", prefix = " WHERE ")
}
