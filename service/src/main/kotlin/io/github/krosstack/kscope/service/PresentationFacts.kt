package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.service.graph.PointLookups
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** How a file is listed by a frontend: as code to edit or read, as a document, or as binary content. */
public enum class ArtifactKind { CODE, DOCUMENT, BINARY }

/**
 * The [ArtifactKind] of each of [files] that the index holds. A file is a document when the index classifies it as
 * documentation, and binary when its first bytes hold a zero byte, as the context compiler decides too; anything
 * else, and a file the index does not know, is code. Nothing here is serialized.
 */
public fun KrosScope.artifactKinds(files: Collection<ArtifactRef>): Map<ArtifactRef, ArtifactKind> {
    memory.load()
    val wanted = files.toSet()
    val locations = workspaceLocations()
    return graph().artifacts().filter { it.ref in wanted }.associate { artifact ->
        artifact.ref to when {
            artifact.type.category == ArtifactCategory.DOCUMENTATION -> ArtifactKind.DOCUMENT
            hasBinaryHead(workspaceRoot, locations.pathOf(artifact.ref)) -> ArtifactKind.BINARY
            else -> ArtifactKind.CODE
        }
    }
}

private fun hasBinaryHead(root: Path, path: String): Boolean {
    val file = root.resolve(path).normalize()
    if (!file.startsWith(root)) return false
    return try {
        Files.newInputStream(file).use { input -> input.readNBytes(HEAD_BYTES).any { it == ZERO } }
    } catch (_: IOException) {
        false
    }
}

private const val HEAD_BYTES = 8_192
private const val ZERO: Byte = 0

/**
 * What a declaration, or a file, is directly connected to. [declared] holds the declarations nested in a symbol or
 * declared in a file; [dependsOn] and [referredBy] hold the resolved relationships that leave and enter the unit,
 * read from the whole persisted graph rather than from a ranked, limited result. Nothing here is serialized.
 */
public class UnitNeighbourhood(
    public val symbol: Symbol?,
    public val declared: List<Symbol>,
    public val dependsOn: List<Relationship>,
    public val referredBy: List<Relationship>,
)

/**
 * The declarations whose id, qualified name or simple name is [text], which is what `symbol` lists before any ranked
 * match. Only the rows of those declarations are read.
 */
public fun KrosScope.declarationsNamed(text: String): List<Symbol> {
    memory.requireIndexed()
    return PointLookups(graph()).symbolsMatching(text)
}

/** The neighbourhood of [node], a symbol id or a workspace path; empty when the index has neither. */
public fun KrosScope.neighbourhood(node: String): UnitNeighbourhood {
    memory.requireIndexed()
    val graph = graph()
    val lookups = PointLookups(graph)
    val file = lookups.artifactsAt(node).firstOrNull()
    val symbol = graph.symbol(SymbolId(node))
    val declared = declaredBy(symbol, file, lookups).sortedBy { it.id.value }
    val unit = unitNodes(file, listOfNotNull(symbol) + declared)
    return UnitNeighbourhood(
        symbol = symbol,
        declared = declared,
        dependsOn = unit.flatMap { graph.outgoing(it) }
            .filter { it.kind in LINKS && it.target()?.let { target -> target !in unit } == true },
        referredBy = unit.flatMap { graph.incoming(it) }.filter { it.kind in LINKS && it.from !in unit },
    )
}

private fun declaredBy(symbol: Symbol?, file: Artifact?, lookups: PointLookups): List<Symbol> = when {
    symbol != null -> lookups.descendants(symbol.id)
    file != null -> lookups.declaredIn(file.ref)
    else -> emptyList()
}

private fun unitNodes(file: Artifact?, symbols: List<Symbol>): Set<NodeRef> =
    (listOfNotNull(file?.let { NodeRef.OfArtifact(it.ref) }) + symbols.map { NodeRef.OfSymbol(it.id) }).toSet()

private fun Relationship.target(): NodeRef? = (resolution as? Resolution.Resolved)?.target

private val LINKS = RelationKind.entries.toSet() - setOf(RelationKind.CONTAINS, RelationKind.CO_CHANGES_WITH)
