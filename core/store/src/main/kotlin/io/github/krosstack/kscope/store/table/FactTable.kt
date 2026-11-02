package io.github.krosstack.kscope.store.table

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor
import io.github.krosstack.kscope.store.sqlite.Database
import io.github.krosstack.kscope.store.sqlite.TableSpec
import io.github.krosstack.kscope.store.sqlite.WhereClause
import io.github.krosstack.kscope.store.structure.BuildRootTable
import io.github.krosstack.kscope.store.structure.ModuleTable
import io.github.krosstack.kscope.store.structure.RepositoryTable
import io.github.krosstack.kscope.store.structure.SourceSetTable
import io.github.krosstack.kscope.store.structure.TargetTable
import io.github.krosstack.kscope.store.structure.VariantTable

internal interface FactTable<T : Any> {
    val spec: TableSpec

    fun bind(binder: Binder, value: T)

    fun read(cursor: Cursor): T

    fun wrap(value: T): Fact

    fun unwrap(fact: Fact): T?
}

internal fun <T : Any> Database.select(table: FactTable<T>, where: WhereClause = WhereClause()): List<T> =
    query("SELECT ${table.spec.selectList} FROM ${table.spec.name}${where.toSql()}", where.parameters, table::read)

internal object FactTables {
    val all: List<FactTable<*>> = listOf(
        RepositoryTable,
        BuildRootTable,
        ModuleTable,
        TargetTable,
        VariantTable,
        SourceSetTable,
        ArtifactTable,
        SymbolTable,
        RelationshipTable,
        SymbolicReferenceTable,
        MetricTable,
        DiagnosticTable,
    )
}
