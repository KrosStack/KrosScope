package io.github.krosstack.kscope.service.graph

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.WorkspaceLocations
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

/**
 * Finds symbols and artifacts with the indexed queries of the reader, so that looking a name up reads the rows that
 * match and not the graph. Every answer equals what filtering the full lists would give: a symbol that several units
 * declare under one id counts as the one that readers see, and files are named by workspace path.
 */
internal class PointLookups(private val graph: GraphReader) {
    val locations: WorkspaceLocations by lazy { WorkspaceLocations(graph.structure().repositories) }

    /** The files that a workspace path names; a path that leaves out the directory of its repository names none. */
    fun artifactsAt(workspacePath: String): List<Artifact> =
        locations.artifactsNamed(workspacePath).mapNotNull(graph::artifact)

    /** The files that [path] names in the repositories of a workspace of several, where it names no file. */
    fun artifactsWithoutDirectory(path: String): List<Artifact> {
        if (!locations.severalRepositories) return emptyList()
        return locations.withinEachRepository(path).mapNotNull(graph::artifact)
    }

    /** The symbol with this id; if there is none, the symbols with this qualified name or simple name. */
    fun symbolsNamed(query: String): List<Symbol> = listOfNotNull(graph.symbol(SymbolId(query))).ifEmpty {
        byName(query).filter { it.qualifiedName == query || it.name == query }
    }

    /** The symbols whose id, qualified name or simple name is [text]. */
    fun symbolsMatching(text: String): List<Symbol> {
        val found = listOfNotNull(graph.symbol(SymbolId(text))) + byName(text)
        return found.distinctBy { it.id }.filter { it.id.value == text || it.qualifiedName == text || it.name == text }
    }

    fun declaredIn(file: ArtifactRef): List<Symbol> =
        preferred(graph.symbols(SymbolQuery(artifact = file, limit = ALL))) { it.declaration?.artifact == file }

    fun declaredAtPath(workspacePath: String): List<Symbol> =
        locations.artifactsNamed(workspacePath).flatMap(::declaredIn)

    private fun children(parent: SymbolId): List<Symbol> =
        preferred(graph.symbols(SymbolQuery(container = parent, limit = ALL))) { it.container == parent }

    /** The symbols nested in [root] at any depth. A symbol is never nested in itself, so a loop of containers ends. */
    fun descendants(root: SymbolId): List<Symbol> {
        val found = mutableListOf<Symbol>()
        val reached = mutableSetOf(root)
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            children(queue.removeFirst()).filter { reached.add(it.id) }.forEach {
                found += it
                queue.addLast(it.id)
            }
        }
        return found
    }

    private fun byName(text: String): List<Symbol> {
        val named = graph.symbols(SymbolQuery(qualifiedName = text, limit = ALL)) +
            graph.symbols(SymbolQuery(name = text, limit = ALL))
        return preferred(named) { true }
    }

    private fun preferred(found: List<Symbol>, stillMatches: (Symbol) -> Boolean): List<Symbol> =
        found.distinctBy { it.id }.mapNotNull { graph.symbol(it.id) }.filter(stillMatches)

    private companion object {
        const val ALL = Int.MAX_VALUE
    }
}
