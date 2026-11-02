package io.github.krosstack.kscope.analyzer.schema.sqldelight.synthesis

import io.github.krosstack.kscope.analyzer.schema.sqldelight.SqSource
import io.github.krosstack.kscope.analyzer.schema.sqldelight.SqlDelightNaming
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlTable
import io.github.krosstack.kscope.model.SymbolId

/** The classes of tables: where each table is defined decides the package of its class and the symbol it comes from. */
internal class TableClasses(sources: List<SqSource>) {
    private val definitions: Map<String, SqSource> =
        sources.flatMap { source -> source.tables.map { it.name.lowercase() to source } }.toMap()

    fun qualifiedName(table: SqlTable): String = SqlDelightNaming.qualified(
        definitions[table.name.lowercase()]?.packageName.orEmpty(),
        SqlDelightNaming.tableClass(table.name),
    )

    fun symbolOf(table: SqlTable): SymbolId? = definitions[table.name.lowercase()]?.ids?.tableId(table.name)
}
