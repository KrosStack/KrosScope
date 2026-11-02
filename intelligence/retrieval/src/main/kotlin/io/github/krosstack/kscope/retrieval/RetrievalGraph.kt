package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.WorkspaceLocations
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery

internal class RetrievalGraph(val reader: GraphReader) {
    val structure = reader.structure()
    val artifacts = reader.artifacts().associateBy { it.ref }
    val symbols = reader.symbols(SymbolQuery(limit = Int.MAX_VALUE)).associateBy { it.id }
    val edges = reader.relationships(RelationshipQuery(limit = Int.MAX_VALUE))
    val outgoing = edges.groupBy { it.from }
    val incoming = edges.mapNotNull { edge -> edge.target()?.let { it to edge } }
        .groupBy({ it.first }, { it.second })
    val sourceSets = structure.sourceSets.associateBy { it.id }
    private val repositoriesByBuildRoot = structure.buildRoots.associate { it.id to it.repository }
    val locations = WorkspaceLocations(structure.repositories)
    val tests = artifacts.values.filter { artifact ->
        artifact.sourceSet?.let { sourceSets[it]?.role }?.let { it != SourceSetRole.MAIN } == true
    }.map { it.ref }.toSet()

    fun file(node: NodeRef): ArtifactRef? = file(node, emptySet())

    fun declaredFile(node: NodeRef): ArtifactRef? = when (node) {
        is NodeRef.OfArtifact -> node.ref
        is NodeRef.OfSymbol -> symbols[node.id]?.declaration?.artifact
        else -> null
    }

    private fun file(node: NodeRef, seen: Set<NodeRef>): ArtifactRef? {
        if (node in seen) return null
        return when (node) {
            is NodeRef.OfArtifact -> artifacts[node.ref]?.generated?.sourceOfTruth
                ?.let { file(it, seen + node) } ?: node.ref

            is NodeRef.OfSymbol -> {
                val symbol = symbols[node.id]
                val origin = symbol?.origin as? SymbolOrigin.Generated
                val source = origin?.sourceOfTruth ?: incoming[node].orEmpty()
                    .firstOrNull { it.kind == RelationKind.GENERATES }?.from
                source?.let { file(it, seen + node) } ?: symbol?.declaration?.artifact
            }

            else -> null
        }
    }

    fun module(file: ArtifactRef) = artifacts[file]?.sourceSet?.let { sourceSets[it]?.module }
        ?: structure.modules.filter { module ->
            val directory = module.directory.value
            repositoriesByBuildRoot[module.buildRoot] == file.repository &&
                (directory.isEmpty() || file.path.value.startsWith("$directory/"))
        }.maxByOrNull { it.directory.value.length }?.id

    private val declaredIn: Map<ArtifactRef, List<DeclaredSymbol>> by lazy {
        buildMap<ArtifactRef, MutableList<DeclaredSymbol>> {
            symbols.values.forEachIndexed { position, symbol ->
                val file = symbol.declaration?.artifact ?: return@forEachIndexed
                getOrPut(file, ::mutableListOf).add(DeclaredSymbol(position, symbol.id))
            }
        }
    }

    fun nodes(files: Set<ArtifactRef>): Set<NodeRef> = buildSet {
        files.forEach { add(NodeRef.OfArtifact(it)) }
        files.flatMap { declaredIn[it].orEmpty() }.sortedBy(DeclaredSymbol::position)
            .forEach { add(NodeRef.OfSymbol(it.id)) }
    }

    fun resolve(text: String): List<NodeRef> = buildList {
        artifacts.keys.filter { locations.pathOf(it) == text }.forEach { add(NodeRef.OfArtifact(it)) }
        symbols.values.filter { it.id.value == text || it.qualifiedName == text || it.name == text }
            .forEach { add(NodeRef.OfSymbol(it.id)) }
        structure.modules.filter { it.id.value == text || it.path == text }.forEach { add(NodeRef.OfModule(it.id)) }
    }.sortedBy(::nodeKey)
}

private class DeclaredSymbol(val position: Int, val id: SymbolId)

internal fun Relationship.target(): NodeRef? = (resolution as? Resolution.Resolved)?.target

internal fun nodeKey(node: NodeRef): String = when (node) {
    is NodeRef.OfArtifact -> "artifact:${node.ref.repository.value}:${node.ref.path.value}"
    is NodeRef.OfSymbol -> "symbol:${node.id.value}"
    is NodeRef.OfModule -> "module:${node.id.value}"
    is NodeRef.OfSourceSet -> "source-set:${node.id.value}"
    is NodeRef.OfExternal -> "external:${node.coordinate}"
}
