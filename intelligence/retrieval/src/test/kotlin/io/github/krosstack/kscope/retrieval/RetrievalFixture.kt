package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.spi.graph.CoverageReport
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure

internal class RetrievalFixture(
    private val files: List<Artifact> = emptyList(),
    private val edges: List<Relationship> = emptyList(),
    private val sourceSets: List<SourceSet> = emptyList(),
) : GraphReader {
    override fun structure() = WorkspaceStructure(
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        sourceSets,
    )
    override fun artifacts() = files
    override fun artifact(ref: ArtifactRef) = files.find { it.ref == ref }
    override fun symbol(id: SymbolId): Symbol? = null
    override fun symbols(query: SymbolQuery): List<Symbol> = emptyList()
    override fun outgoing(node: NodeRef, kinds: Set<RelationKind>) = edges.filter {
        it.from == node && (kinds.isEmpty() || it.kind in kinds)
    }
    override fun incoming(node: NodeRef, kinds: Set<RelationKind>) = edges.filter {
        it.target() == node && (kinds.isEmpty() || it.kind in kinds)
    }
    override fun relationships(query: RelationshipQuery) = edges.take(query.limit)
    override fun symbolicReferences(namespace: String?): List<SymbolicReference> = emptyList()
    override fun diagnostics(): List<Diagnostic> = emptyList()
    override fun coverage() = CoverageReport(emptyList(), emptyMap(), emptyMap())
}

internal fun artifact(path: String): Artifact = Artifact(
    ArtifactRef(RepositoryId("test"), RepoPath(path)),
    ArtifactType("kotlin.source", ArtifactCategory.SOURCE),
    ContentHash("hash"),
    10,
    1,
    null,
    null,
)

internal fun node(path: String): NodeRef = NodeRef.OfArtifact(artifact(path).ref)

internal fun relationship(
    from: NodeRef,
    to: NodeRef,
    kind: RelationKind = RelationKind.CALLS,
    certainty: Certainty = Certainty.EXACT,
): Relationship = Relationship(
    from,
    kind,
    null,
    Resolution.Resolved(to, certainty),
    EvidenceClass.COMPILER,
    null,
    Scope(),
)
