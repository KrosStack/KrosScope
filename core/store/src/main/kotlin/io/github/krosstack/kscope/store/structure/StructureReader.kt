package io.github.krosstack.kscope.store.structure

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure
import io.github.krosstack.kscope.store.read.CanonicalOrder
import io.github.krosstack.kscope.store.sqlite.Database
import io.github.krosstack.kscope.store.sqlite.WhereClause
import io.github.krosstack.kscope.store.table.ArtifactTable
import io.github.krosstack.kscope.store.table.DiagnosticTable
import io.github.krosstack.kscope.store.table.FactTable
import io.github.krosstack.kscope.store.table.select

internal class StructureReader(private val database: Database) {
    fun structure(): WorkspaceStructure = WorkspaceStructure(
        repositories = distinctById(RepositoryTable) { it.id },
        buildRoots = distinctById(BuildRootTable) { it.id },
        modules = distinctById(ModuleTable) { it.id },
        targets = distinctById(TargetTable) { it.id },
        variants = distinctById(VariantTable) { it.id },
        sourceSets = distinctById(SourceSetTable) { it.id },
    )

    fun artifacts(): List<Artifact> = distinctById(ArtifactTable) { it.ref }

    fun artifact(ref: ArtifactRef): Artifact? {
        val where = WhereClause().equal("repository", ref.repository.value).equal("path", ref.path.value)
        return CanonicalOrder.sorted(database.select(ArtifactTable, where), ArtifactTable::wrap).firstOrNull()
    }

    fun diagnostics(): List<Diagnostic> = CanonicalOrder.sorted(database.select(DiagnosticTable), DiagnosticTable::wrap)

    private fun <T : Any, K> distinctById(table: FactTable<T>, id: (T) -> K): List<T> =
        CanonicalOrder.sorted(database.select(table), table::wrap).distinctBy(id)
}
