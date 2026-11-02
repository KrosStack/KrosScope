package io.github.krosstack.kscope.analyzer.android.linking

import io.github.krosstack.kscope.analyzer.android.resources.ResourceKey
import io.github.krosstack.kscope.analyzer.android.resources.ResourceSymbols
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId

/** A symbol that a reference may resolve to, with the module and source set that declare it. */
internal class LinkCandidate(val symbolId: SymbolId, val module: ModuleId, val sourceSet: SourceSetId?)

/** The resource symbols of the graph by module and resource. */
internal class ResourceIndex(symbols: List<Symbol>) {
    private val entries: Map<Pair<ModuleId, ResourceKey>, List<LinkCandidate>> = symbols
        .filter { it.kind.id.startsWith(ResourceSymbols.KIND_PREFIX) }
        .mapNotNull(::entryOf)
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, found) -> found.sortedBy { it.symbolId.value } }

    fun definitions(module: ModuleId, key: ResourceKey): List<LinkCandidate> = entries[module to key].orEmpty()

    private fun entryOf(symbol: Symbol): Pair<Pair<ModuleId, ResourceKey>, LinkCandidate>? {
        val identity = (symbol.id.identity() as? ParsedSymbolId.Valid)?.identity ?: return null
        val module = ModuleId(identity.unit)
        val key = ResourceKey(symbol.kind.id.removePrefix(ResourceSymbols.KIND_PREFIX), symbol.name)
        return (module to key) to LinkCandidate(symbol.id, module, symbol.scope.sourceSet)
    }
}
