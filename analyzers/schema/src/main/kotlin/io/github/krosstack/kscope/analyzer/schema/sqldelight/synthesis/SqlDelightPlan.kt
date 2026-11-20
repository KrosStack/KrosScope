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

private fun StubClass.rowShape() = constructor.orEmpty().map { it.name to it.type }

/** A stub file planned for a `.sq` file or a database, in the source set whose `Placement` it names. */
internal class PlannedStub(val placement: Placement, val virtualPath: String, val file: StubFile)

/** A declaration that [source] would generate again after an earlier file of its source set, by its qualified name. */
internal class RepeatedDeclaration(val source: SqSource, val qualifiedName: String)

/**
 * The Kotlin that SQLDelight 2 generates for the `.sq` files of a module, by its naming rules: a data class for each
 * table, a `<File>Queries` class with a function for each query, a class for the rows of a query that selects columns
 * of several tables or computed ones, and the interface of each database with a property for each queries class. A
 * class or property that a later file of the same source set or database would generate again is made once, from the
 * first file by path.
 */
internal class SqlDelightPlan(private val sources: List<SqSource>) {
    private val tableClasses = TableClasses(sources)
    private val schemas: Map<String?, SqlSchema> =
        sources.groupBy { it.database?.name }.mapValues { (_, group) -> SqlSchema(group.flatMap { it.tables }) }
    private val queriesBySource = sources.associateWith(::queriesOf)
    private val byPath = sources.sortedBy { it.artifact.ref.path.value }
    private val rowClassOwners = byPath.flatMap { source ->
        queriesBySource.getValue(source).mapNotNull { it.rowClass }.map { rowClass ->
            classKey(source, rowClass.name) to rowClass
        }
    }.distinctBy { it.first }.toMap()
    private val tableClassesBySource = sources.associateWith { source ->
        source.tables.filter { it.columns.isNotEmpty() }.map { tableClass(source, it) }
    }
    private val queriesClassBySource = sources.associateWith { queriesClass(it, queriesBySource.getValue(it)) }
    private val fileClasses = sources.associateWith { source ->
        tableClassesBySource.getValue(source) + listOfNotNull(queriesClassBySource.getValue(source))
    }
    private val classOwners = byPath.filter { it.placement.sourceSet != null }.flatMap { source ->
        fileClasses.getValue(source).map { classKey(source, it.name) to source }
    }.distinctBy { it.first }.toMap()
    private val propertySources = byPath.filter { it.database != null && it.file.queries.isNotEmpty() }
        .groupBy { checkNotNull(it.database) }
        .mapValues { (_, group) -> group.distinctBy { SqlDelightNaming.queriesProperty(it.path.baseName) } }

    /** The classes and database properties left out because an earlier file already generates them. */
    val repeated: List<RepeatedDeclaration> = byPath.flatMap { source ->
        fileClasses.getValue(source).filter { source.placement.sourceSet != null && !owns(source, it) }
            .map { RepeatedDeclaration(source, SqlDelightNaming.qualified(source.packageName, it.name)) }
    } + byPath.filter { source ->
        source.database != null && source.file.queries.isNotEmpty() &&
            source !in propertySources.getValue(checkNotNull(source.database))
    }.map { source ->
        val database = checkNotNull(source.database)
        val property = SqlDelightNaming.queriesProperty(source.path.baseName)
        RepeatedDeclaration(source, SqlDelightNaming.qualified(database.packageName, "${database.name}.$property"))
    }

    fun stubs(): List<PlannedStub> = sources.mapNotNull(::fileStub) + databaseStubs()

    private fun owns(source: SqSource, stubClass: StubClass): Boolean =
        classOwners[classKey(source, stubClass.name)] === source

    private fun fileStub(source: SqSource): PlannedStub? {
        val classes = tableClassesBySource.getValue(source).filter { owns(source, it) } + ownedRowClasses(source) +
            listOfNotNull(queriesClassBySource.getValue(source)?.takeIf { owns(source, it) })
        val virtualPath = "sqldelight/${source.artifact.ref.path.value.replace('/', '_')}.kt"
        val file = classes.takeIf { it.isNotEmpty() }?.let { StubFile(source.packageName, it) }
        return file?.let { PlannedStub(source.placement, virtualPath, it) }
    }

    private fun queriesOf(source: SqSource): List<QueryStubs> {
        val analyzer = QueryAnalyzer(schemas.getValue(source.database?.name))
        val queryStubs = SqlDelightQueryStubs(tableClasses, source.packageName)
        return source.file.queries.map { statement ->
            val label = checkNotNull(statement.label)
            queryStubs.of(label, analyzer.shapeOf(statement), source.ids.queryId(source.path.baseName, label))
        }
    }

    private fun ownedRowClasses(source: SqSource): List<StubClass> =
        queriesBySource.getValue(source).mapNotNull { it.rowClass }.filter { rowClass ->
            rowClassOwners.getValue(classKey(source, rowClass.name)) === rowClass
        }

    private fun functionsOf(source: SqSource, query: QueryStubs): List<StubFunction> {
        val rowClass = query.rowClass ?: return query.functions
        val owner = rowClassOwners.getValue(classKey(source, rowClass.name))
        return if (rowClass === owner || rowClass.rowShape() == owner.rowShape()) {
            query.functions
        } else {
            query.functions.map { it.copy(certainty = Certainty.HEURISTIC) }
        }
    }

    private fun classKey(source: SqSource, name: String) =
        Triple(source.placement.sourceSet?.id, source.packageName, name)

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
        queries.flatMap { functionsOf(source, it) }.takeIf { it.isNotEmpty() }?.let { functions ->
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
            val file = StubFile(database.packageName, listOf(databaseClass(database)))
            PlannedStub(host, "sqldelight/${database.name}.kt", file)
        }

    private fun databaseClass(database: SqlDelightDatabase): StubClass {
        val queryProperties = propertySources[database].orEmpty().map { source ->
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
