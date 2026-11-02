package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.store.table.SymbolTable

/** Of the symbols that several units declare under one id, the one readers see: a source origin, then the first. */
internal object PreferredSymbol {
    fun among(candidates: List<Symbol>): Symbol? {
        if (candidates.size < 2) return candidates.firstOrNull()
        return CanonicalOrder.sorted(candidates, SymbolTable::wrap).minByOrNull { rank(it.origin) }
    }

    private fun rank(origin: SymbolOrigin): Int = if (origin is SymbolOrigin.Source) 0 else 1
}
