package io.github.krosstack.kscope.service.graph

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery

/**
 * A reader over the facts that were loaded for one question. The lists are in the canonical order of the full graph,
 * so an analysis that reads them sees the same relative order as when it reads the whole index. The structure, the
 * diagnostics, the coverage, the metrics and the symbolic references come from [source], which holds them whole.
 */
internal class MaterializedGraph(
    private val source: GraphReader,
    private val artifacts: List<Artifact>,
    private val symbols: List<Symbol>,
    private val relationships: List<Relationship>,
) : GraphReader by source {
    override fun artifacts(): List<Artifact> = artifacts

    override fun artifact(ref: ArtifactRef): Artifact? = artifacts.firstOrNull { it.ref == ref }

    override fun symbol(id: SymbolId): Symbol? = symbols.firstOrNull { it.id == id }

    override fun symbols(query: SymbolQuery): List<Symbol> = symbols.filter { query.accepts(it) }.take(query.limit)

    override fun outgoing(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> =
        relationships.filter { it.from == node && (kinds.isEmpty() || it.kind in kinds) }

    override fun incoming(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> =
        relationships.filter { it.target() == node && (kinds.isEmpty() || it.kind in kinds) }

    override fun relationships(query: RelationshipQuery): List<Relationship> =
        relationships.filter { query.accepts(it) }.take(query.limit)
}

private fun Relationship.target(): NodeRef? = (resolution as? Resolution.Resolved)?.target

private fun SymbolQuery.accepts(symbol: Symbol): Boolean = namesMatch(symbol) && placeMatches(symbol)

private fun SymbolQuery.namesMatch(symbol: Symbol): Boolean = (name == null || symbol.name == name) &&
    (qualifiedName == null || symbol.qualifiedName == qualifiedName) &&
    (kindId == null || symbol.kind.id == kindId) &&
    (category == null || symbol.kind.category == category)

private fun SymbolQuery.placeMatches(symbol: Symbol): Boolean =
    (artifact == null || symbol.declaration?.artifact == artifact) &&
        (container == null || symbol.container == container) &&
        (flag == null || flag in symbol.flags)

private fun RelationshipQuery.accepts(edge: Relationship): Boolean = (kinds.isEmpty() || edge.kind in kinds) &&
    (subKind == null || edge.subKind == subKind) &&
    (evidenceClass == null || edge.evidenceClass == evidenceClass) &&
    (resolved == null || (edge.target() != null) == resolved)
