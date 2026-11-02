package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.spi.graph.CoverageReport
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.NodeMetrics
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure
import io.github.krosstack.kscope.store.sqlite.Database
import io.github.krosstack.kscope.store.structure.StructureReader

internal class SqliteGraphReader(database: Database) :
    GraphReader,
    NodeMetrics by MetricReader(database) {
    private val structureReader = StructureReader(database)
    private val symbolReader = SymbolReader(database)
    private val relationshipReader = RelationshipReader(database)
    private val coverageReader = CoverageReader(database)

    override fun structure(): WorkspaceStructure = structureReader.structure()

    override fun artifacts(): List<Artifact> = structureReader.artifacts()

    override fun artifact(ref: ArtifactRef): Artifact? = structureReader.artifact(ref)

    override fun symbol(id: SymbolId): Symbol? = symbolReader.symbol(id)

    override fun symbols(query: SymbolQuery): List<Symbol> = symbolReader.symbols(query)

    override fun outgoing(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> =
        relationshipReader.outgoing(node, kinds)

    override fun incoming(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> =
        relationshipReader.incoming(node, kinds)

    override fun relationships(query: RelationshipQuery): List<Relationship> = relationshipReader.relationships(query)

    override fun symbolicReferences(namespace: String?): List<SymbolicReference> =
        relationshipReader.symbolicReferences(namespace)

    override fun diagnostics(): List<Diagnostic> = structureReader.diagnostics()

    override fun coverage(): CoverageReport = coverageReader.coverage()
}
