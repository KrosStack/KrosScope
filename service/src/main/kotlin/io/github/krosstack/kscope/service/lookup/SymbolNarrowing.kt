package io.github.krosstack.kscope.service.lookup

import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.retrieval.SymbolResult
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.NodeResolution
import io.github.krosstack.kscope.service.resolveNode

public fun KrosScope.symbolWithin(query: String, resolved: String, narrowing: RepositoryNarrowing?): NarrowedSymbol {
    if (narrowing == null) return NarrowedSymbol(symbol(resolved), SearchOrigin())
    val declared = graph().symbol(SymbolId(resolved))
    val home = declared?.declaration?.artifact?.repository
    val inside = symbol(resolved, narrowing.repository)
    return when {
        declared == null -> searched(resolved, inside, narrowing)
        home == null || home == narrowing.repository -> limited(inside, narrowing)
        declared.isIdentity(query) -> limited(inside, narrowing, outside = namedRepository(home))
        else -> widened(symbol(resolved), narrowing)
    }
}

/** Narrows the candidates of a simple name to [narrowing] before the caller decides whether the name is ambiguous. */
public fun KrosScope.resolveWithin(
    query: String,
    askedFrom: String?,
    narrowing: RepositoryNarrowing?,
): NarrowedResolution {
    val resolution = resolveNode(query, askedFrom)
    val inside = narrowing?.let { insideCandidates(resolution, query, it) }
    return if (inside == null) {
        NarrowedResolution(resolution)
    } else {
        NarrowedResolution(resolution.copy(candidates = inside), SearchOrigin(narrowing))
    }
}

private fun KrosScope.insideCandidates(
    resolution: NodeResolution,
    query: String,
    narrowing: RepositoryNarrowing,
): List<String>? {
    val symbols = resolution.candidates.map { graph().symbol(SymbolId(it)) }
    val simpleName = symbols.all { it != null && it.name == query && it.id.value != query }
    return resolution.candidates.filterIndexed { index, _ ->
        symbols[index]?.declaration?.artifact?.repository.let { it == null || it == narrowing.repository }
    }.takeIf { simpleName && resolution.candidates.size > 1 && it.size > 1 }
}

private fun KrosScope.searched(resolved: String, inside: SymbolResult, narrowing: RepositoryNarrowing): NarrowedSymbol =
    searchedInside(inside, narrowing) { symbol(resolved) }

internal fun searchedInside(
    inside: SymbolResult,
    narrowing: RepositoryNarrowing,
    everywhere: () -> SymbolResult,
): NarrowedSymbol {
    if (inside.symbols.isNotEmpty() || inside.searchCapped) return limited(inside, narrowing)
    val widest = everywhere()
    return if (widest.symbols.isEmpty()) limited(inside, narrowing) else widened(widest, narrowing)
}

private fun limited(
    result: SymbolResult,
    narrowing: RepositoryNarrowing,
    outside: RepositoryNarrowing? = null,
): NarrowedSymbol = NarrowedSymbol(result, SearchOrigin(narrowing, outside, incomplete = result.searchCapped))

private fun widened(result: SymbolResult, narrowing: RepositoryNarrowing): NarrowedSymbol =
    NarrowedSymbol(result, SearchOrigin(widenedFrom = narrowing))

private fun KrosScope.namedRepository(id: RepositoryId): RepositoryNarrowing =
    checkNotNull(graph().structure().repositories.firstOrNull { it.id == id }).asNarrowing()

private fun Symbol.isIdentity(query: String): Boolean = !(name == query && id.value != query && qualifiedName != query)
