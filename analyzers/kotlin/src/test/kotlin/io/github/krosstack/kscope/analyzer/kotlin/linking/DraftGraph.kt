package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.spi.graph.CoverageReport
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure

/** A graph that answers the queries of the linkers from lists, with every criterion of a query honoured. */
internal class DraftGraph(
    private val structure: WorkspaceStructure,
    private val symbols: List<Symbol>,
    private val relationships: List<Relationship>,
    private val artifacts: List<Artifact>,
) : GraphReader {
    override fun structure(): WorkspaceStructure = structure

    override fun artifacts(): List<Artifact> = artifacts

    override fun artifact(ref: ArtifactRef): Artifact? = artifacts.firstOrNull { it.ref == ref }

    override fun symbol(id: SymbolId): Symbol? = symbols.firstOrNull { it.id == id }

    override fun symbols(query: SymbolQuery): List<Symbol> = symbols
        .filter { query.name == null || it.name == query.name }
        .filter { query.qualifiedName == null || it.qualifiedName == query.qualifiedName }
        .filter { query.kindId == null || it.kind.id == query.kindId }
        .filter { query.category == null || it.kind.category == query.category }
        .filter { query.artifact == null || it.declaration?.artifact == query.artifact }
        .filter { query.container == null || it.container == query.container }
        .filter { query.flag == null || query.flag in it.flags }
        .sortedBy { it.id.value }
        .take(query.limit)

    override fun outgoing(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> =
        relationships.filter { it.from == node && (kinds.isEmpty() || it.kind in kinds) }

    override fun incoming(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> = relationships.filter {
        (it.resolution as? Resolution.Resolved)?.target == node && (kinds.isEmpty() || it.kind in kinds)
    }

    override fun relationships(query: RelationshipQuery): List<Relationship> = relationships
        .filter { query.kinds.isEmpty() || it.kind in query.kinds }
        .filter { query.subKind == null || it.subKind == query.subKind }
        .filter { query.evidenceClass == null || it.evidenceClass == query.evidenceClass }
        .filter { query.resolved == null || (it.resolution is Resolution.Resolved) == query.resolved }
        .take(query.limit)

    override fun symbolicReferences(namespace: String?): List<SymbolicReference> = emptyList()

    override fun diagnostics(): List<Diagnostic> = emptyList()

    override fun coverage(): CoverageReport = CoverageReport(emptyList(), emptyMap(), emptyMap())
}
