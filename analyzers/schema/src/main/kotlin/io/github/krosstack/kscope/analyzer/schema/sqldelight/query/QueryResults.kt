package io.github.krosstack.kscope.analyzer.schema.sqldelight.query

import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.ColumnType
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.ParsedSql
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SelectColumn
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqKind
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlExpression
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlTypes

private const val UNNAMED = "expr"

private val long = ColumnType("Long", false, false)
private val double = ColumnType("Double", false, false)
private val string = ColumnType("String", false, false)
private val unknown = ColumnType("Any", true, true)

/**
 * What the function of a query returns. `SELECT *` from one table is a row of it; one column that cannot be null is
 * its type; anything else is a projection class with a property for each selected column.
 */
internal class QueryResults(private val scope: QueryScope) {
    fun of(kind: SqKind, parsed: ParsedSql?): QueryResult = when {
        kind != SqKind.SELECT -> QueryResult.Mutation
        parsed == null || parsed.selectList.isEmpty() -> QueryResult.Unknown
        else -> select(parsed.selectList)
    }

    private fun select(items: List<SelectColumn>): QueryResult {
        val only = items.singleOrNull()
        val oneTable = only?.takeIf { it.isStar }?.let { scope.tableFor(it.qualifier ?: onlyTableName()) }
            ?.takeUnless { it.reference.isOptional }
        val scalar = only?.takeUnless { it.isStar }?.let { typeOf(it.expression) }?.takeUnless { it.isNullable }
        return when {
            oneTable != null -> oneTable.table?.let { QueryResult.RowOfTable(it) } ?: QueryResult.Unknown
            scalar != null -> QueryResult.Single(scalar)
            else -> projection(items)
        }
    }

    private fun onlyTableName(): String? = scope.tables.singleOrNull()?.reference?.name

    private fun projection(items: List<SelectColumn>): QueryResult {
        val columns = items.flatMap(::columnsOf)
        return if (columns.isEmpty()) QueryResult.Unknown else QueryResult.Projection(withUniqueNames(columns))
    }

    private fun columnsOf(item: SelectColumn): List<ResultColumn> = when {
        !item.isStar -> listOf(ResultColumn(nameOf(item), typeOf(item.expression)))
        item.qualifier != null -> tableColumns(scope.tableFor(item.qualifier))
        else -> scope.tables.flatMap { tableColumns(it) }
    }

    private fun tableColumns(entry: ScopeTable?): List<ResultColumn> = entry?.let { scoped ->
        scoped.table?.columns.orEmpty().map { ResultColumn(it.name, scoped.resultTypeOf(it)) }
    }.orEmpty()

    private fun nameOf(item: SelectColumn): String =
        item.alias ?: (item.expression as? SqlExpression.ColumnReference)?.name ?: UNNAMED

    private fun typeOf(expression: SqlExpression): ColumnType = when (expression) {
        is SqlExpression.ColumnReference -> scope.resultType(expression.qualifier, expression.name) ?: unknown
        is SqlExpression.Literal -> ColumnType(expression.kotlinType, false, false)
        is SqlExpression.Cast -> SqlTypes.kotlinTypeOf(expression.sqlType, true)
        is SqlExpression.Call -> callType(expression)
        SqlExpression.Other -> unknown
    }

    private fun callType(call: SqlExpression.Call): ColumnType {
        val argument = call.arguments.firstOrNull()?.let(::typeOf)
        return when (call.function) {
            "COUNT", "LENGTH", "RANDOM", "INSTR", "LAST_INSERT_ROWID", "CHANGES", "TOTAL_CHANGES" -> long
            "MAX", "MIN", "SUM" -> argument?.nullable() ?: unknown
            "AVG" -> double.nullable()
            "TOTAL" -> double
            "LOWER", "UPPER", "TRIM", "LTRIM", "RTRIM", "SUBSTR", "REPLACE", "HEX" -> string
            else -> unknown
        }
    }

    // Columns that share a name get underscores until each is unique.
    private fun withUniqueNames(columns: List<ResultColumn>): List<ResultColumn> {
        val used = mutableSetOf<String>()
        return columns.map { column ->
            ResultColumn(generateSequence(column.name) { "${it}_" }.first { used.add(it) }, column.type)
        }
    }
}
