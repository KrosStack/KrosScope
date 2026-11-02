package io.github.krosstack.kscope.analyzer.schema.sqldelight.synthesis

import io.github.krosstack.kscope.analyzer.schema.sqldelight.SqlDelightNaming
import io.github.krosstack.kscope.analyzer.schema.sqldelight.query.ParameterType
import io.github.krosstack.kscope.analyzer.schema.sqldelight.query.QueryResult
import io.github.krosstack.kscope.analyzer.schema.sqldelight.query.QueryShape
import io.github.krosstack.kscope.analyzer.schema.sqldelight.query.ResultColumn
import io.github.krosstack.kscope.analyzer.schema.stub.StubClass
import io.github.krosstack.kscope.analyzer.schema.stub.StubForm
import io.github.krosstack.kscope.analyzer.schema.stub.StubFunction
import io.github.krosstack.kscope.analyzer.schema.stub.StubParameter
import io.github.krosstack.kscope.analyzer.schema.stub.StubTruth
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.SymbolId

private const val QUERY = "app.cash.sqldelight.Query"
private const val MUTATION_RESULT = "app.cash.sqldelight.db.QueryResult<Long>"

/** The function or functions SQLDelight generates for a query, and the class of its rows if it has one. */
internal class QueryStubs(val functions: List<StubFunction>, val rowClass: StubClass?)

/** The parts of the functions of a query that its overloads share. */
private class FunctionMaker(
    private val label: String,
    private val parameters: List<StubParameter>,
    private val truth: StubTruth,
    private val certainty: Certainty,
    private val typeSource: SymbolId?,
) {
    fun make(returns: String, extra: List<StubParameter> = emptyList(), typeParameters: String? = null) = StubFunction(
        label,
        parameters + extra,
        returns,
        typeParameters,
        truth = truth,
        certainty = certainty,
        typeSource = typeSource,
    )
}

/**
 * The stubs of one query. A `SELECT` gets a function that returns a `Query` of its rows, and one that also takes a
 * mapper from the columns to the result; a statement that changes data gets a function that executes it. The rows
 * are the class of the table, one value or a class named for the query with a property for each column.
 */
internal class SqlDelightQueryStubs(private val tableClasses: TableClasses, private val packageName: String) {
    fun of(label: String, shape: QueryShape, queryId: SymbolId): QueryStubs {
        val truth = StubTruth(queryId, direct = true)
        val certainty = if (shape.isGuess) Certainty.HEURISTIC else Certainty.EXACT
        val parameters = shape.parameters.map { StubParameter(it.name, typeOf(it.type)) }
        val rows = (shape.result as? QueryResult.RowOfTable)?.table?.let(tableClasses::symbolOf)
        val maker = FunctionMaker(label, parameters, truth, certainty, rows)
        val rowClassName = SqlDelightNaming.projectionClass(label)
        val functions = when (val result = shape.result) {
            QueryResult.Mutation -> listOf(maker.make(MUTATION_RESULT))

            QueryResult.Unknown -> listOf(maker.make("$QUERY<Any>"))

            is QueryResult.Single -> listOf(maker.make("$QUERY<${result.type.written}>"))

            is QueryResult.RowOfTable ->
                queryFunctions(maker, tableClasses.qualifiedName(result.table), columnsOf(result))

            is QueryResult.Projection -> queryFunctions(maker, qualified(rowClassName), result.columns)
        }
        val rowClass = (shape.result as? QueryResult.Projection)?.let { rowClassOf(rowClassName, it, truth) }
        return QueryStubs(functions, rowClass)
    }

    private fun queryFunctions(maker: FunctionMaker, rowType: String, columns: List<ResultColumn>): List<StubFunction> {
        val arguments = columns.joinToString(", ") { "${it.name}: ${it.type.written}" }
        val mapper = StubParameter("mapper", "($arguments) -> T")
        return listOf(maker.make("$QUERY<T>", listOf(mapper), "T : Any"), maker.make("$QUERY<$rowType>"))
    }

    private fun rowClassOf(name: String, projection: QueryResult.Projection, truth: StubTruth) = StubClass(
        name = name,
        form = StubForm.DATA_CLASS,
        constructor = projection.columns.map { StubParameter(it.name, it.type.written, isProperty = true) },
        truth = truth.copy(direct = false),
    )

    private fun typeOf(type: ParameterType): String = when (type) {
        is ParameterType.OfColumn -> type.type.written
        is ParameterType.Many -> "Collection<${type.element.base}>"
        is ParameterType.Row -> tableClasses.qualifiedName(type.table)
        ParameterType.Count -> "Long"
        ParameterType.Unknown -> "Any?"
    }

    private fun columnsOf(result: QueryResult.RowOfTable): List<ResultColumn> =
        result.table.columns.map { ResultColumn(it.name, it.type) }

    private fun qualified(name: String) = SqlDelightNaming.qualified(packageName, name)
}
