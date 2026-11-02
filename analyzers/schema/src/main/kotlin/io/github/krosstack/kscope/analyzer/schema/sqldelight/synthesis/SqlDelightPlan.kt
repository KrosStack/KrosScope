package io.github.krosstack.kscope.analyzer.schema.sqldelight.synthesis

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.sqldelight.SqSource
import io.github.krosstack.kscope.analyzer.schema.sqldelight.SqlDelightDatabase
import io.github.krosstack.kscope.analyzer.schema.sqldelight.SqlDelightNaming
import io.github.krosstack.kscope.analyzer.schema.sqldelight.query.QueryAnalyzer
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlSchema
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlTable
import io.github.krosstack.kscope.analyzer.schema.stub.StubClass
import io.github.krosstack.kscope.analyzer.schema.stub.StubFile
import io.github.krosstack.kscope.analyzer.schema.stub.StubForm
import io.github.krosstack.kscope.analyzer.schema.stub.StubFunction
import io.github.krosstack.kscope.analyzer.schema.stub.StubParameter
import io.github.krosstack.kscope.analyzer.schema.stub.StubProperty
import io.github.krosstack.kscope.analyzer.schema.stub.StubTruth
import io.github.krosstack.kscope.model.Certainty

private const val TRANSACTER = "app.cash.sqldelight.Transacter"
private const val SQL_DRIVER = "app.cash.sqldelight.db.SqlDriver"
private const val SQL_SCHEMA = "app.cash.sqldelight.db.SqlSchema<app.cash.sqldelight.db.QueryResult.Value<Unit>>"

/** A stub file planned for a `.sq` file or a database, in the source set whose `Placement` it names. */
internal class PlannedStub(val placement: Placement, val virtualPath: String, val file: StubFile)

/**
 * The Kotlin that SQLDelight 2 generates for the `.sq` files of a module, by its naming rules: a data class for each
 * table, a `<File>Queries` class with a function for each query, a class for the rows of a query that selects columns
 * of several tables or computed ones, and the interface of each database with a property for each queries class.
 */
internal class SqlDelightPlan(private val sources: List<SqSource>) {
    private val tableClasses = TableClasses(sources)
    private val schemas: Map<String?, SqlSchema> =
        sources.groupBy { it.database?.name }.mapValues { (_, group) -> SqlSchema(group.flatMap { it.tables }) }

    fun stubs(): List<PlannedStub> = sources.mapNotNull(::fileStub) + databaseStubs()

    private fun fileStub(source: SqSource): PlannedStub? {
        val analyzer = QueryAnalyzer(schemas.getValue(source.database?.name))
        val queryStubs = SqlDelightQueryStubs(tableClasses, source.packageName)
        val queries = source.file.queries.map { statement ->
            val label = checkNotNull(statement.label)
            queryStubs.of(label, analyzer.shapeOf(statement), source.ids.queryId(source.path.baseName, label))
        }
        val classes = source.tables.filter { it.columns.isNotEmpty() }.map { tableClass(source, it) } +
            queries.mapNotNull { it.rowClass } + listOfNotNull(queriesClass(source, queries))
        val virtualPath = "sqldelight/${source.artifact.ref.path.value.replace('/', '_')}.kt"
        val file = classes.takeIf { it.isNotEmpty() }?.let { StubFile(source.packageName, it) }
        return file?.let { PlannedStub(source.placement, virtualPath, it) }
    }

    private fun tableClass(source: SqSource, table: SqlTable) = StubClass(
        name = SqlDelightNaming.tableClass(table.name),
        form = StubForm.DATA_CLASS,
        constructor = table.columns.map { column ->
            StubParameter(
                name = column.name,
                type = column.type.written,
                isProperty = true,
                truth = StubTruth(source.ids.columnId(table.name, column.name), direct = true),
                certainty = if (column.type.isGuess) Certainty.HEURISTIC else Certainty.EXACT,
            )
        },
        truth = StubTruth(source.ids.tableId(table.name), direct = true),
    )

    private fun queriesClass(source: SqSource, queries: List<QueryStubs>): StubClass? =
        queries.flatMap { it.functions }.takeIf { it.isNotEmpty() }?.let { functions ->
            StubClass(
                name = SqlDelightNaming.queriesClass(source.path.baseName),
                form = StubForm.CLASS,
                constructor = listOf(StubParameter("driver", SQL_DRIVER)),
                superTypes = "app.cash.sqldelight.TransacterImpl(driver)",
                members = functions,
            )
        }

    private fun databaseStubs(): List<PlannedStub> =
        sources.filter { it.database != null }.groupBy { checkNotNull(it.database) }.map { (database, group) ->
            val host = group.groupBy { it.placement.sourceSet?.id }.values.maxBy { it.size }.first().placement
            val file = StubFile(database.packageName, listOf(databaseClass(database, group)))
            PlannedStub(host, "sqldelight/${database.name}.kt", file)
        }

    private fun databaseClass(database: SqlDelightDatabase, group: List<SqSource>): StubClass {
        val queryProperties = group.filter { it.file.queries.isNotEmpty() }.map { source ->
            val queriesName = SqlDelightNaming.queriesClass(source.path.baseName)
            val type = SqlDelightNaming.qualified(source.packageName, queriesName)
            StubProperty(SqlDelightNaming.queriesProperty(source.path.baseName), type, initializer = null)
        }.sortedBy { it.name }
        val companion = StubClass(
            name = "Companion",
            form = StubForm.COMPANION,
            members = listOf(
                StubProperty("Schema", SQL_SCHEMA),
                StubFunction("invoke", listOf(StubParameter("driver", SQL_DRIVER)), database.name, isOperator = true),
                StubFunction("allTableNames", emptyList(), "List<String>"),
            ),
        )
        return StubClass(
            name = database.name,
            form = StubForm.INTERFACE,
            superTypes = TRANSACTER,
            members = queryProperties,
            nested = listOf(companion),
        )
    }
}
