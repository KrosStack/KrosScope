package io.github.krosstack.kscope.service.graph

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.service.UnitNeighbourhood
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

/** The lookups as they were before they read a few rows: each one filters the full lists of the graph. */
internal object ReferenceLookups {
    fun everySymbol(graph: GraphReader): List<Symbol> = graph.symbols(SymbolQuery(limit = Int.MAX_VALUE))

    /** Names, ids, paths, locations and module paths that the sample holds, and a few that it does not. */
    fun queries(graph: GraphReader): List<String> {
        val symbols = everySymbol(graph)
        val locations = symbols.mapNotNull { it.declaration }.flatMap {
            listOf("${it.artifact.path.value}:${it.range.startLine}", "${it.artifact.path.value}:${it.range.endLine}")
        }
        val names = symbols.flatMap { listOfNotNull(it.id.value, it.name, it.qualifiedName) }
        val paths = graph.artifacts().map { it.ref.path.value }
        val modules = graph.structure().modules.flatMap { listOf(it.path, it.id.value) }
        return (names + paths + locations + modules + listOf("Nothing_Here", "x.kt:0", "sample.tests")).distinct()
    }

    fun resolution(graph: GraphReader, query: String): List<String> {
        val symbols = everySymbol(graph)
        val split = query.lastIndexOf(':')
        val line = query.substring(split + 1).toIntOrNull()
        val path = if (split >= 0) query.substring(0, split) else query
        val isLocation = line != null && line > 0 && graph.artifacts().any { it.ref.path.value == path }
        val matches = if (isLocation) {
            atLine(symbols, path, checkNotNull(line))
        } else {
            symbols.filter { it.id.value == query }.ifEmpty {
                symbols.filter { it.qualifiedName == query || it.name == query }
            }
        }
        return matches.map { it.id.value }.distinct().sorted().ifEmpty { listOf(query) }
    }

    fun neighbourhood(graph: GraphReader, node: String): UnitNeighbourhood {
        val symbols = everySymbol(graph)
        val symbol = graph.symbol(SymbolId(node))
        val file = graph.artifacts().firstOrNull { it.ref.path.value == node }
        val declared = if (symbol != null) {
            nested(symbol, symbols)
        } else {
            file?.let { f -> symbols.filter { it.declaration?.artifact == f.ref }.sortedBy { it.id.value } }.orEmpty()
        }
        val members = (listOfNotNull(symbol) + declared).map { NodeRef.OfSymbol(it.id) }
        val unit = (listOfNotNull(file?.let { NodeRef.OfArtifact(it.ref) }) + members).toSet()
        val links = RelationKind.entries.toSet() - setOf(RelationKind.CONTAINS, RelationKind.CO_CHANGES_WITH)
        val leaving = unit.flatMap { graph.outgoing(it) }.filter { edge ->
            edge.kind in links && target(edge)?.let { it !in unit } == true
        }
        val entering = unit.flatMap { graph.incoming(it) }.filter { it.kind in links && it.from !in unit }
        return UnitNeighbourhood(symbol, declared, leaving, entering)
    }

    private fun nested(root: Symbol, symbols: List<Symbol>): List<Symbol> {
        val children = symbols.filter { it.container != null }.groupBy { it.container }
        val found = mutableListOf<Symbol>()
        val queue = ArrayDeque(listOf(root.id))
        while (queue.isNotEmpty()) {
            children[queue.removeFirst()].orEmpty().forEach {
                found += it
                queue.addLast(it.id)
            }
        }
        return found.sortedBy { it.id.value }
    }

    private fun atLine(symbols: List<Symbol>, path: String, line: Int): List<Symbol> {
        val containing = symbols.filter { symbol ->
            val declaration = symbol.declaration
            val range = declaration?.range
            declaration != null && range != null && declaration.artifact.path.value == path &&
                line >= range.startLine &&
                (line < range.endLine || (line == range.endLine && range.endColumn > 1))
        }
        val shortest = containing.minOfOrNull { lines(it) }
        return containing.filter { lines(it) == shortest }
    }

    private fun lines(symbol: Symbol): Int = checkNotNull(symbol.declaration).range.let { it.endLine - it.startLine }

    private fun target(edge: Relationship): NodeRef? = (edge.resolution as? Resolution.Resolved)?.target
}
