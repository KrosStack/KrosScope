package io.github.krosstack.kscope.context.fragment

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.WorkspaceLocations
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery

internal class ContextGraph(reader: GraphReader) {
    val structure = reader.structure()
    val locations = WorkspaceLocations(structure.repositories)
    val artifacts = reader.artifacts().associateBy { it.ref }
    val symbols = reader.symbols(SymbolQuery(limit = Int.MAX_VALUE)).associateBy { it.id }
    val edges = reader.relationships(RelationshipQuery(limit = Int.MAX_VALUE))
    val declarations = symbols.values.filter { it.declaration != null }.groupBy { it.declaration?.artifact }
    private val incoming = edges.mapNotNull { edge -> edge.target()?.let { it to edge } }
        .groupBy({ it.first }, { it.second })
    private val moduleBySourceSet = buildMap { structure.sourceSets.forEach { putIfAbsent(it.id, it.module.value) } }
    private val resolvedArtifacts = HashMap<NodeRef, ArtifactRef?>()

    fun artifact(node: NodeRef): ArtifactRef? {
        if (resolvedArtifacts.containsKey(node)) return resolvedArtifacts[node]
        return artifact(node, emptySet()).also { resolvedArtifacts[node] = it }
    }

    private fun artifact(node: NodeRef, seen: Set<NodeRef>): ArtifactRef? {
        if (node in seen) return null
        return when (node) {
            is NodeRef.OfArtifact -> {
                val file = artifacts[node.ref]
                if (file?.generated == null) {
                    file?.ref
                } else {
                    file.generated?.sourceOfTruth?.let { artifact(it, seen + node) }
                }
            }

            is NodeRef.OfSymbol -> symbolArtifact(node, seen)

            is NodeRef.OfModule, is NodeRef.OfSourceSet, is NodeRef.OfExternal -> null
        }
    }

    private fun symbolArtifact(node: NodeRef.OfSymbol, seen: Set<NodeRef>): ArtifactRef? {
        val symbol = symbols[node.id] ?: return null
        val origin = (symbol.origin as? SymbolOrigin.Generated)?.sourceOfTruth
        val generatedBy = incoming[node].orEmpty().filter { it.kind == RelationKind.GENERATES }.map { it.from }
        val sources = origin?.let(::listOf) ?: generatedBy
        val declared = symbol.declaration?.artifact?.takeIf { symbol.origin !is SymbolOrigin.Generated }
        return sources.firstNotNullOfOrNull { artifact(it, seen + node) }
            ?: declared?.let { artifact(NodeRef.OfArtifact(it), seen + node) }
    }

    fun generated(node: NodeRef): Boolean = when (node) {
        is NodeRef.OfArtifact -> artifacts[node.ref]?.generated != null

        is NodeRef.OfSymbol -> symbols[node.id]?.let { symbol ->
            symbol.origin is SymbolOrigin.Generated || artifacts[symbol.declaration?.artifact]?.generated != null
        } == true

        is NodeRef.OfModule, is NodeRef.OfSourceSet, is NodeRef.OfExternal -> false
    }

    fun nodes(file: ArtifactRef): List<NodeRef> = listOf(NodeRef.OfArtifact(file)) +
        declarations[file].orEmpty().map { NodeRef.OfSymbol(it.id) }

    fun neighbors(nodes: Set<NodeRef>): List<NodeRef> = edges.filter { it.kind == RelationKind.CALLS }
        .flatMap { edge ->
            val target = edge.target()
            when {
                edge.from in nodes && target != null -> listOf(target)
                target in nodes -> listOf(edge.from)
                else -> emptyList()
            }
        }.distinct().sortedBy(::contextNodeKey)

    fun module(file: ArtifactRef): String = artifacts[file]?.sourceSet?.let(moduleBySourceSet::get)
        ?: file.repository.value

    fun signature(symbol: Symbol): String = listOfNotNull(
        symbol.container?.let { symbols[it]?.qualifiedName ?: symbols[it]?.name },
        symbol.signature ?: symbol.qualifiedName ?: symbol.name,
    ).joinToString(" :: ").replace('\n', ' ').replace('\r', ' ')
}

internal fun Relationship.target(): NodeRef? = (resolution as? Resolution.Resolved)?.target

internal fun contextNodeKey(node: NodeRef): String = when (node) {
    is NodeRef.OfArtifact -> "artifact:${node.ref.repository.value}:${node.ref.path.value}"
    is NodeRef.OfSymbol -> "symbol:${node.id.value}"
    is NodeRef.OfModule -> "module:${node.id.value}"
    is NodeRef.OfSourceSet -> "source-set:${node.id.value}"
    is NodeRef.OfExternal -> "external:${node.coordinate}"
}
