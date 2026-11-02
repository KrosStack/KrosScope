package io.github.krosstack.kscope.analyzer.schema.sqldelight.sql

import io.github.krosstack.kscope.analyzer.schema.Span

/** The Kotlin type of a column. [base] has no nullability mark; [isGuess] says the SQL type was not one we know. */
internal class ColumnType(val base: String, val isNullable: Boolean, val isGuess: Boolean) {
    val written: String get() = if (isNullable) "$base?" else base

    fun nullable() = ColumnType(base, true, isGuess)
}

internal class SqlColumn(
    val name: String,
    val sqlType: String,
    val type: ColumnType,
    val declaration: Span,
    val nameSpan: Span,
)

internal class SqlTable(val name: String, val columns: List<SqlColumn>, val declaration: Span, val nameSpan: Span) {
    fun column(columnName: String): SqlColumn? = columns.firstOrNull { it.name.equals(columnName, ignoreCase = true) }
}

/** The tables of the `.sq` files of a database, by name regardless of case, as SQLite compares names. */
internal class SqlSchema(tables: List<SqlTable>) {
    val tables: List<SqlTable> = tables.distinctBy { it.name.lowercase() }
    private val byName = this.tables.associateBy { it.name.lowercase() }

    fun table(name: String): SqlTable? = byName[name.lowercase()]
}

private val sqlTypes: Map<String, String> = buildMap {
    listOf("INTEGER", "BIGINT", "INT8", "UNSIGNED BIG INT").forEach { put(it, "Long") }
    listOf("INT", "MEDIUMINT").forEach { put(it, "Int") }
    listOf("SMALLINT", "INT2").forEach { put(it, "Short") }
    put("TINYINT", "Byte")
    put("BOOLEAN", "Boolean")
    listOf("TEXT", "VARCHAR", "CHAR", "CHARACTER", "NCHAR", "NVARCHAR", "CLOB").forEach { put(it, "String") }
    listOf("REAL", "DOUBLE", "FLOAT").forEach { put(it, "Double") }
    put("BLOB", "ByteArray")
}

/** The Kotlin types that SQLDelight gives the SQL types of its SQLite dialect. */
internal object SqlTypes {
    fun kotlinTypeOf(sqlType: String, isNullable: Boolean): ColumnType {
        val known = sqlTypes[sqlType.uppercase()]
        return ColumnType(known ?: "Any", isNullable, isGuess = known == null)
    }
}
