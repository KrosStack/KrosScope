package io.github.krosstack.kscope.analyzer.schema.sqldelight

import io.github.krosstack.kscope.analyzer.schema.SchemaSymbol
import io.github.krosstack.kscope.analyzer.schema.Span
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqStatement
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlColumn
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlTable
import io.github.krosstack.kscope.model.SymbolId

private const val MAX_SIGNATURE = 160

/** The symbols that a `.sq` file declares: its tables, their columns and its labelled queries. */
internal class SqlDelightDeclarations(private val source: SqSource) {
    private val ids = source.ids

    fun symbols(): List<SchemaSymbol> = source.tables.flatMap(::tableSymbols) + source.file.queries.map(::querySymbol)

    private fun tableSymbols(table: SqlTable): List<SchemaSymbol> {
        val tableId = ids.tableId(table.name)
        val qualified = ids.tableQualifiedName(table.name)
        val own = SchemaSymbol(
            id = tableId,
            kind = SqlDelightKinds.table,
            name = table.name,
            qualifiedName = qualified,
            signature = "CREATE TABLE ${table.name}",
            container = null,
            declaration = table.declaration,
            nameSpan = table.nameSpan,
        )
        return listOf(own) + table.columns.map { columnSymbol(table, it, tableId, qualified) }
    }

    private fun columnSymbol(table: SqlTable, column: SqlColumn, container: SymbolId, tableName: String) = SchemaSymbol(
        id = ids.columnId(table.name, column.name),
        kind = SqlDelightKinds.column,
        name = column.name,
        qualifiedName = "$tableName.${column.name}",
        signature = condensed(source.text.substring(column.declaration.start, column.declaration.end)),
        container = container,
        declaration = column.declaration,
        nameSpan = column.nameSpan,
        attributes = mapOf(
            "sqldelight.type" to column.sqlType,
            "sqldelight.kotlin-type" to column.type.written,
        ),
    )

    private fun querySymbol(statement: SqStatement): SchemaSymbol {
        val label = checkNotNull(statement.label)
        val queriesName = SqlDelightNaming.queriesClass(source.path.baseName)
        val queriesClass = SqlDelightNaming.qualified(source.packageName, queriesName)
        return SchemaSymbol(
            id = ids.queryId(source.path.baseName, label),
            kind = SqlDelightKinds.query,
            name = label,
            qualifiedName = "$queriesClass.$label",
            signature = condensed("$label: ${statement.text}"),
            container = null,
            declaration = statement.declaration,
            nameSpan = statement.labelSpan ?: Span(statement.declaration.start, statement.declaration.start),
            attributes = mapOf("sqldelight.statement" to statement.kind.name.lowercase()),
        )
    }

    private fun condensed(text: String): String = text.replace(Regex("\\s+"), " ").trim().let {
        if (it.length > MAX_SIGNATURE) it.take(MAX_SIGNATURE) + "..." else it
    }
}
