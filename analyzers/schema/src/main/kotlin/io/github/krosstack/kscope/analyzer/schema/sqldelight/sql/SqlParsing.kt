package io.github.krosstack.kscope.analyzer.schema.sqldelight.sql

import net.sf.jsqlparser.JSQLParserException
import net.sf.jsqlparser.expression.CastExpression
import net.sf.jsqlparser.expression.DoubleValue
import net.sf.jsqlparser.expression.Expression
import net.sf.jsqlparser.expression.Function
import net.sf.jsqlparser.expression.LongValue
import net.sf.jsqlparser.expression.StringValue
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.schema.Column
import net.sf.jsqlparser.schema.Table
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.insert.Insert
import net.sf.jsqlparser.statement.select.AllColumns
import net.sf.jsqlparser.statement.select.AllTableColumns
import net.sf.jsqlparser.statement.select.Join
import net.sf.jsqlparser.statement.select.ParenthesedSelect
import net.sf.jsqlparser.statement.select.PlainSelect
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.select.SelectItem
import net.sf.jsqlparser.statement.select.SetOperationList
import net.sf.jsqlparser.statement.update.Update
import net.sf.jsqlparser.util.TablesNamesFinder

/**
 * A table in the `FROM` clause, a join or the target of a statement, with the alias that names it in the statement.
 * [isOptional] says that an outer join may leave its columns without a row.
 */
internal class TableRef(val name: String, val alias: String?, val isOptional: Boolean = false) {
    fun isNamed(qualifier: String): Boolean =
        qualifier.equals(alias ?: name, ignoreCase = true) || qualifier.equals(name, ignoreCase = true)
}

/** The parts of an expression that give a result column its type. */
internal sealed interface SqlExpression {
    class ColumnReference(val qualifier: String?, val name: String) : SqlExpression

    class Call(val function: String, val arguments: List<SqlExpression>) : SqlExpression

    class Literal(val kotlinType: String) : SqlExpression

    class Cast(val sqlType: String) : SqlExpression

    data object Other : SqlExpression
}

/** An item of the select list: `*`, `table.*` or an expression with an optional alias. */
internal class SelectColumn(
    val isStar: Boolean,
    val qualifier: String?,
    val expression: SqlExpression,
    val alias: String?,
)

/**
 * What the analysis needs to know about a statement after parsing: [tables] it names in `FROM` and joins, the [target]
 * it changes, the [selectList], the [insertColumns], and every table it [reads] or [writes].
 */
internal class ParsedSql(
    val tables: List<TableRef>,
    val target: TableRef?,
    val selectList: List<SelectColumn>,
    val insertColumns: List<String>?,
    val referenced: List<String>,
) {
    val writes: List<String> get() = listOfNotNull(target?.name)
    val reads: List<String> get() = referenced.filterNot { name -> target?.name.equals(name, ignoreCase = true) }
}

/**
 * The SQL parser library in use, JSqlParser, behind a small model. SQLDelight statements are normalised first, because
 * the library does not know the conflict clauses of SQLite's `INSERT` and `UPDATE`.
 */
internal object SqlParsing {
    /** Null when the library cannot parse the statement. */
    fun parse(text: String): ParsedSql? {
        val statement = try {
            CCJSqlParserUtil.parse(SqlNormalisation.of(text))
        } catch (_: JSQLParserException) {
            return null
        }
        return ParsedSql(
            tables = fromTables(statement),
            target = targetOf(statement)?.let(::refOf),
            selectList = selectList(statement),
            insertColumns = (statement as? Insert)?.columns?.map { it.columnName.unquoted() },
            referenced = TablesNamesFinder<Void>().getTables(statement).map { it.substringAfterLast('.').unquoted() },
        )
    }

    private fun targetOf(statement: Statement): Table? = when (statement) {
        is Insert -> statement.table
        is Update -> statement.table
        is Delete -> statement.table
        else -> null
    }

    private fun fromTables(statement: Statement): List<TableRef> = when (statement) {
        is PlainSelect -> joined(statement.fromItem as? Table, statement.joins.orEmpty())

        is Update -> (listOf(statement.table) + statement.joins.orEmpty().mapNotNull { it.fromItem as? Table })
            .map { refOf(it) }

        is Insert, is Delete -> listOfNotNull(targetOf(statement)).map { refOf(it) }

        else -> emptyList()
    }

    // The side of an outer join that may have no row is optional: the joined table of a left join, the tables before a
    // right join and both sides of a full join.
    private fun joined(from: Table?, joins: List<Join>): List<TableRef> =
        joins.fold(listOfNotNull(from).map { refOf(it) }) { tables, join ->
            val isEarlierOptional = join.isRight || join.isFull
            val earlier = tables.map { TableRef(it.name, it.alias, it.isOptional || isEarlierOptional) }
            earlier + listOfNotNull(join.fromItem as? Table).map { refOf(it, isOptional = join.isLeft || join.isFull) }
        }

    private fun refOf(table: Table, isOptional: Boolean = false) =
        TableRef(table.name.unquoted(), table.alias?.name?.unquoted(), isOptional)

    private fun selectList(statement: Statement): List<SelectColumn> = when (val select = firstSelect(statement)) {
        null -> emptyList()
        else -> select.selectItems.map(::columnOf)
    }

    private fun firstSelect(statement: Statement): PlainSelect? = when (statement) {
        is PlainSelect -> statement
        is SetOperationList -> statement.selects.firstOrNull()?.let(::firstSelect)
        is ParenthesedSelect -> firstSelect(statement.select)
        else -> null
    }

    private fun firstSelect(select: Select): PlainSelect? = firstSelect(select as Statement)

    private fun columnOf(item: SelectItem<*>): SelectColumn = when (val expression = item.expression) {
        is AllTableColumns -> SelectColumn(true, expression.table.name.unquoted(), SqlExpression.Other, null)
        is AllColumns -> SelectColumn(true, null, SqlExpression.Other, null)
        else -> SelectColumn(false, null, expressionOf(expression), item.alias?.name?.unquoted())
    }

    private fun expressionOf(expression: Expression): SqlExpression = when (expression) {
        is Column -> SqlExpression.ColumnReference(expression.table?.name?.unquoted(), expression.columnName.unquoted())

        is Function -> SqlExpression.Call(
            expression.name.uppercase(),
            expression.parameters?.map(::expressionOf).orEmpty(),
        )

        is LongValue -> SqlExpression.Literal("Long")

        is DoubleValue -> SqlExpression.Literal("Double")

        is StringValue -> SqlExpression.Literal("String")

        is CastExpression ->
            SqlExpression.Cast(expression.columnDefinitions.firstOrNull()?.colDataType?.dataType.orEmpty())

        is ParenthesedExpressionList<*> -> expression.singleOrNull()?.let(::expressionOf) ?: SqlExpression.Other

        else -> SqlExpression.Other
    }
}

internal fun String.unquoted(): String = removeSurrounding("\"").removeSurrounding("`").removeSurrounding("[", "]")
