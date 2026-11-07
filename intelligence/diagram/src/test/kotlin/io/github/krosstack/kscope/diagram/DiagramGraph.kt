package io.github.krosstack.kscope.diagram

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

internal class DiagramGraph(order: Int = 0) : GraphReader {
    private val fixture = DiagramFacts(order)

    override fun structure(): WorkspaceStructure = fixture.structure()

    override fun relationships(query: RelationshipQuery): List<Relationship> = fixture.facts.filter {
        (query.kinds.isEmpty() || it.kind in query.kinds)
    }

    override fun outgoing(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> =
        relationships(RelationshipQuery(kinds = kinds)).filter { it.from == node }

    override fun incoming(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> = relationships(
        RelationshipQuery(kinds = kinds),
    ).filter { (it.resolution as? Resolution.Resolved)?.target == node }

    override fun artifacts(): List<Artifact> = emptyList()
    override fun artifact(ref: ArtifactRef): Artifact? = null
    override fun symbol(id: SymbolId): Symbol? = null
    override fun symbols(query: SymbolQuery): List<Symbol> = emptyList()
    override fun symbolicReferences(namespace: String?): List<SymbolicReference> = emptyList()
    override fun diagnostics(): List<Diagnostic> = emptyList()
    override fun coverage(): CoverageReport = CoverageReport(emptyList(), emptyMap(), emptyMap())
}
