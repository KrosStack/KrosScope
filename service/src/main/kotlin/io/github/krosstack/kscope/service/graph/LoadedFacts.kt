package io.github.krosstack.kscope.service.graph

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.graph.GraphReader

/**
 * The rows loaded so far. A node that is loaded in full has its symbol or artifact and every relationship that
 * leaves or enters it; the rows that say where a generated node comes from are followed. Each file that a loaded node
 * is declared in is loaded with the symbols declared in it and the relationships that leave them.
 */
internal class LoadedFacts(private val graph: GraphReader, private val limit: Int) {
    private val lookups = PointLookups(graph)
    private val symbols = HashMap<SymbolId, Keyed<Symbol>>()
    private val artifacts = HashMap<ArtifactRef, Keyed<Artifact>>()
    private val leaving = HashMap<NodeRef, List<Keyed<Relationship>>>()
    private val entering = HashMap<NodeRef, List<Keyed<Relationship>>>()
    private val files = LinkedHashSet<ArtifactRef>()
    private val filesLoaded = HashSet<ArtifactRef>()
    val loaded: MutableSet<NodeRef> = HashSet()

    /** Loads [start] and what they lead to; false when that is more than the limit allows. */
    fun load(start: Set<NodeRef>): Boolean {
        val queue = ArrayDeque(start)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (!loaded.add(node)) continue
            if (loaded.size > limit) return false
            queue.addAll(loadInFull(node))
        }
        files.filter(filesLoaded::add).forEach(::loadFile)
        return symbols.size + artifacts.size <= limit
    }

    fun materialize(): MaterializedGraph {
        val sources = leaving.keys
        val relationships = leaving.values.flatten() + entering.values.flatten().filter { it.value.from !in sources }
        return MaterializedGraph(
            graph,
            artifacts.values.canonical(),
            symbols.values.canonical(),
            relationships.canonical(),
        )
    }

    private fun loadInFull(node: NodeRef): List<NodeRef> {
        leaving.getOrPut(node) { graph.outgoing(node).map(::keyed) }
        val incoming = graph.incoming(node)
        entering[node] = incoming.map(::keyed)
        return when (node) {
            is NodeRef.OfSymbol -> generatedFrom(node, incoming)
            is NodeRef.OfArtifact -> artifactSource(node)
            else -> emptyList()
        }
    }

    private fun generatedFrom(node: NodeRef.OfSymbol, incoming: List<Relationship>): List<NodeRef> {
        val symbol = graph.symbol(node.id) ?: return emptyList()
        symbols.getOrPut(symbol.id) { keyed(symbol) }
        symbol.declaration?.artifact?.let(files::add)
        val origin = (symbol.origin as? SymbolOrigin.Generated)?.sourceOfTruth
        return listOfNotNull(origin) + incoming.filter { it.kind == RelationKind.GENERATES }.map { it.from }
    }

    private fun artifactSource(node: NodeRef.OfArtifact): List<NodeRef> {
        val artifact = graph.artifact(node.ref) ?: return emptyList()
        artifacts.getOrPut(artifact.ref) { keyed(artifact) }
        files += artifact.ref
        return listOfNotNull(artifact.generated?.sourceOfTruth)
    }

    private fun loadFile(file: ArtifactRef) {
        graph.artifact(file)?.let { found -> artifacts.getOrPut(file) { keyed(found) } }
        val fileNode = NodeRef.OfArtifact(file)
        leaving.getOrPut(fileNode) { graph.outgoing(fileNode).map(::keyed) }
        lookups.declaredIn(file).forEach { symbol ->
            symbols.getOrPut(symbol.id) { keyed(symbol) }
            val node = NodeRef.OfSymbol(symbol.id)
            leaving.getOrPut(node) { graph.outgoing(node).map(::keyed) }
        }
    }

    private fun <T> Collection<Keyed<T>>.canonical(): List<T> = sortedBy { it.key }.map { it.value }

    private fun keyed(value: Artifact) = Keyed(value, encode(Fact.OfArtifact(value)))

    private fun keyed(value: Symbol) = Keyed(value, encode(Fact.OfSymbol(value)))

    private fun keyed(value: Relationship) = Keyed(value, encode(Fact.OfRelationship(value)))

    private fun encode(fact: Fact): String = KscopeJson.encodeToString(Fact.serializer(), fact)
}

private class Keyed<out T>(val value: T, val key: String)
