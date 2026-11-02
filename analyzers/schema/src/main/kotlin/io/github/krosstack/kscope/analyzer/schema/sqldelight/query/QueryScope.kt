package io.github.krosstack.kscope.analyzer.schema.sqldelight.query

import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.ColumnType
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.ParsedSql
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlColumn
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlSchema
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlTable
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.TableRef

/** A table that a statement names, with its definition if the schema has one. */
internal class ScopeTable(val reference: TableRef, val table: SqlTable?) {
    /** The type of [column] in a result: nullable when an outer join may leave the table without a row. */
    fun resultTypeOf(column: SqlColumn): ColumnType = if (reference.isOptional) column.type.nullable() else column.type
}

/** The tables a statement can see, and how a column name written in it finds its column. */
internal class QueryScope(parsed: ParsedSql?, schema: SqlSchema) {
    val tables: List<ScopeTable> = parsed?.tables.orEmpty().map { ScopeTable(it, schema.table(it.name)) }

    val hasUnknownTable: Boolean get() = tables.any { it.table == null }

    fun tableFor(qualifier: String?): ScopeTable? =
        if (qualifier == null) tables.singleOrNull() else tables.firstOrNull { it.reference.isNamed(qualifier) }

    fun column(qualifier: String?, name: String): SqlColumn? =
        candidates(qualifier).firstNotNullOfOrNull { it.table?.column(name) }

    fun resultType(qualifier: String?, name: String): ColumnType? =
        candidates(qualifier).firstNotNullOfOrNull { entry -> entry.table?.column(name)?.let(entry::resultTypeOf) }

    private fun candidates(qualifier: String?): List<ScopeTable> =
        if (qualifier == null) tables else listOfNotNull(tableFor(qualifier))
}
