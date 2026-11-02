package io.github.krosstack.kscope.analyzer.schema.sqldelight.query

import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.Bind
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.BindContext
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.BindUse
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.ColumnType
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.ParsedSql
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqKind
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqStatement
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlBinds
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlText

private const val DEFAULT_NAME = "value"
private val valuesList = Regex("""\bVALUES\s*\(""", RegexOption.IGNORE_CASE)
private val bareBind = Regex("""^(?:\?\d*|:\w+)$""")

/**
 * The parameters of the function that SQLDelight generates for a query: one for each bind argument, named after the
 * column it is compared with or assigned to, or the `:name` it is written with, and typed as that column.
 */
internal class QueryParameters(
    private val scope: QueryScope,
    private val parsed: ParsedSql?,
    private val statement: SqStatement,
) {
    fun list(): List<QueryParameter> {
        val binds = SqlBinds.of(statement.text)
        val insertContexts = insertContexts(binds)
        val parameters = binds.map { bind ->
            val context = insertContexts[bind.key] ?: bind.context
            QueryParameter(nameOf(bind, context), typeOf(context))
        }
        return withUniqueNames(parameters)
    }

    private fun nameOf(bind: Bind, context: BindContext?): String = bind.name ?: when (context?.use) {
        BindUse.VALUE, BindUse.COLLECTION -> context.column?.let { scope.column(context.qualifier, it)?.name ?: it }
        BindUse.ROW -> scope.tables.firstOrNull()?.let { it.table?.name ?: it.reference.name }
        BindUse.COUNT, null -> null
    } ?: DEFAULT_NAME

    private fun typeOf(context: BindContext?): ParameterType = context?.let(::typeIn) ?: ParameterType.Unknown

    private fun typeIn(context: BindContext): ParameterType? {
        val column = context.column?.let { scope.column(context.qualifier, it) }
        return when (context.use) {
            BindUse.VALUE -> column?.let { ParameterType.OfColumn(it.type) }
            BindUse.COLLECTION -> column?.let { ParameterType.Many(nonNull(it.type)) }
            BindUse.ROW -> scope.tables.firstOrNull()?.table?.let { ParameterType.Row(it) }
            BindUse.COUNT -> ParameterType.Count
        }
    }

    private fun nonNull(type: ColumnType) = ColumnType(type.base, false, type.isGuess)

    // A second argument with the name of an earlier one gets underscores until it is unique, as SQLDelight does.
    private fun withUniqueNames(parameters: List<QueryParameter>): List<QueryParameter> {
        val used = mutableSetOf<String>()
        return parameters.map { parameter ->
            val name = generateSequence(parameter.name) { "${it}_" }.first { used.add(it) }
            QueryParameter(name, parameter.type)
        }
    }

    // In `INSERT INTO t(a, b) VALUES (?, ?)` the argument at each position is for the column at that position.
    private fun insertContexts(binds: List<Bind>): Map<String, BindContext> {
        val text = statement.text
        val open = valuesList.find(text)?.range?.last?.takeIf { statement.kind == SqKind.INSERT }
        val close = open?.let { SqlText.closingParenthesis(text, it) }
        if (open == null || close == null) return emptyMap()
        val columns = parsed?.insertColumns ?: scope.tables.firstOrNull()?.table?.columns?.map { it.name }.orEmpty()
        val boundaries = listOf(open) + SqlText.topLevelSeparators(text, open + 1, close, ',') + close
        val values = boundaries.zipWithNext { from, to -> from + 1 to to }
        return values.zip(columns).mapNotNull { (range, column) ->
            val piece = text.substring(range.first, range.second)
            val bind = binds.firstOrNull { it.offset == range.first + piece.length - piece.trimStart().length }
            bind?.takeIf { bareBind.matches(piece.trim()) }?.let { it.key to BindContext(BindUse.VALUE, null, column) }
        }.toMap()
    }
}
