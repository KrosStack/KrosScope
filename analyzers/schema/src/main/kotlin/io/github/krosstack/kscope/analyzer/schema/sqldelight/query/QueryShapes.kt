package io.github.krosstack.kscope.analyzer.schema.sqldelight.query

import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.ColumnType
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqStatement
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlParsing
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlSchema
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlTable

/** The Kotlin type of a bind argument, before it is written. */
internal sealed interface ParameterType {
    class OfColumn(val type: ColumnType) : ParameterType

    /** A collection of values of a column, as in `WHERE id IN :ids`. */
    class Many(val element: ColumnType) : ParameterType

    class Row(val table: SqlTable) : ParameterType

    data object Count : ParameterType

    data object Unknown : ParameterType
}

internal class QueryParameter(val name: String, val type: ParameterType)

internal class ResultColumn(val name: String, val type: ColumnType)

/** What a query returns: nothing for a statement that changes data, a row of a table, one value or a projection. */
internal sealed interface QueryResult {
    data object Mutation : QueryResult

    class RowOfTable(val table: SqlTable) : QueryResult

    class Single(val type: ColumnType) : QueryResult

    class Projection(val columns: List<ResultColumn>) : QueryResult

    data object Unknown : QueryResult
}

/** The signature of the function SQLDelight generates for a query. [isGuess] says part of it is not inferred. */
internal class QueryShape(val parameters: List<QueryParameter>, val result: QueryResult, val isGuess: Boolean)

/** Works out the parameters and the result of a query from its text and the schema of its database. */
internal class QueryAnalyzer(private val schema: SqlSchema) {
    fun shapeOf(statement: SqStatement): QueryShape {
        val parsed = SqlParsing.parse(statement.text)
        val scope = QueryScope(parsed, schema)
        val parameters = QueryParameters(scope, parsed, statement).list()
        val result = QueryResults(scope).of(statement.kind, parsed)
        val isGuess = parsed == null || scope.hasUnknownTable || result.isGuess() ||
            parameters.any { it.type.isGuess() }
        return QueryShape(parameters, result, isGuess)
    }

    private fun QueryResult.isGuess(): Boolean = when (this) {
        QueryResult.Mutation, is QueryResult.RowOfTable -> false
        QueryResult.Unknown -> true
        is QueryResult.Single -> type.isGuess
        is QueryResult.Projection -> columns.any { it.type.isGuess }
    }

    private fun ParameterType.isGuess(): Boolean = when (this) {
        ParameterType.Unknown -> true
        is ParameterType.OfColumn -> type.isGuess
        is ParameterType.Many -> element.isGuess
        is ParameterType.Row, ParameterType.Count -> false
    }
}
