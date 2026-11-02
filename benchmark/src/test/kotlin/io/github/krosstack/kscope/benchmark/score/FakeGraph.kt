package io.github.krosstack.kscope.benchmark.score

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.spi.graph.CoverageReport
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure

/** A graph held in lists, for tests of what the scorers do with a graph. */
internal class FakeGraph(
    private val symbols: List<Symbol> = emptyList(),
    private val artifacts: List<Artifact> = emptyList(),
    private val modules: List<Module> = emptyList(),
    private val buildRoots: List<BuildRoot> = emptyList(),
    private val relationships: List<Relationship> = emptyList(),
) : GraphReader {
    override fun structure() =
        WorkspaceStructure(emptyList(), buildRoots, modules, emptyList(), emptyList(), emptyList())

    override fun artifacts(): List<Artifact> = artifacts

    override fun artifact(ref: ArtifactRef): Artifact? = artifacts.firstOrNull { it.ref == ref }

    override fun symbol(id: SymbolId): Symbol? = symbols.firstOrNull { it.id == id }

    override fun symbols(query: SymbolQuery): List<Symbol> = symbols

    override fun outgoing(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> = emptyList()

    override fun incoming(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> = emptyList()

    override fun relationships(query: RelationshipQuery): List<Relationship> = relationships

    override fun symbolicReferences(namespace: String?): List<SymbolicReference> = emptyList()

    override fun diagnostics(): List<Diagnostic> = emptyList()

    override fun coverage(): CoverageReport = CoverageReport(emptyList(), emptyMap(), emptyMap())
}
