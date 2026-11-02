package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.store.sqlite.Database
import io.github.krosstack.kscope.store.structure.BuildRootTable
import io.github.krosstack.kscope.store.structure.ModuleTable
import io.github.krosstack.kscope.store.structure.RepositoryTable
import io.github.krosstack.kscope.store.structure.SourceSetTable
import io.github.krosstack.kscope.store.structure.TargetTable
import io.github.krosstack.kscope.store.structure.VariantTable
import io.github.krosstack.kscope.store.table.DiagnosticTable
import io.github.krosstack.kscope.store.table.FactTable

/** How many structure entries and diagnostics an index holds, counted by the database without reading the facts. */
public data class StoreCounts(
    public val repositories: Int,
    public val buildRoots: Int,
    public val modules: Int,
    public val sourceSets: Int,
    public val targets: Int,
    public val variants: Int,
    public val diagnostics: Int,
)

internal class CountReader(private val database: Database) {
    fun counts(): StoreCounts = StoreCounts(
        repositories = distinctIds(RepositoryTable),
        buildRoots = distinctIds(BuildRootTable),
        modules = distinctIds(ModuleTable),
        sourceSets = distinctIds(SourceSetTable),
        targets = distinctIds(TargetTable),
        variants = distinctIds(VariantTable),
        diagnostics = single("SELECT COUNT(*) FROM ${DiagnosticTable.spec.name}"),
    )

    private fun distinctIds(table: FactTable<*>): Int = single("SELECT COUNT(DISTINCT id) FROM ${table.spec.name}")

    private fun single(sql: String): Int = database.query(sql) { it.int() }.single()
}
