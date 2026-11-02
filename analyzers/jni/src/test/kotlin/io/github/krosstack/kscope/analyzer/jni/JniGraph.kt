package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.Fact
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

/** A graph of facts that answers the queries of the JNI linker. */
internal class JniGraph(
    facts: List<Fact>,
    private val artifactList: List<Artifact> = emptyList(),
    private val structure: WorkspaceStructure = NativeModel.structure,
) : GraphReader {
    private val symbolList = facts.filterIsInstance<Fact.OfSymbol>().map { it.value }.distinctBy { it.id }
    private val relationshipList = facts.filterIsInstance<Fact.OfRelationship>().map { it.value }

    override fun structure(): WorkspaceStructure = structure

    override fun artifacts(): List<Artifact> = artifactList

    override fun artifact(ref: ArtifactRef): Artifact? = artifactList.firstOrNull { it.ref == ref }

    override fun symbol(id: SymbolId): Symbol? = symbolList.firstOrNull { it.id == id }

    override fun symbols(query: SymbolQuery): List<Symbol> = symbolList.filter {
        (query.kindId == null || it.kind.id == query.kindId) &&
            (query.name == null || it.name == query.name) &&
            (query.category == null || it.kind.category == query.category) &&
            (query.flag == null || query.flag in it.flags)
    }.take(query.limit)

    override fun outgoing(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> =
        relationshipList.filter { it.from == node && (kinds.isEmpty() || it.kind in kinds) }

    override fun incoming(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> = relationshipList.filter {
        (it.resolution as? Resolution.Resolved)?.target == node && (kinds.isEmpty() || it.kind in kinds)
    }

    override fun relationships(query: RelationshipQuery): List<Relationship> =
        relationshipList.filter { query.kinds.isEmpty() || it.kind in query.kinds }.take(query.limit)

    override fun symbolicReferences(namespace: String?): List<SymbolicReference> = emptyList()

    override fun diagnostics(): List<Diagnostic> = emptyList()

    override fun coverage() = CoverageReport(emptyList(), emptyMap(), emptyMap())
}
