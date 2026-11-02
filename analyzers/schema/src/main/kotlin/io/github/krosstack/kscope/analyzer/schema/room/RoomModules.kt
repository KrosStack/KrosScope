package io.github.krosstack.kscope.analyzer.schema.room

import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.Symbol

/** Tells the symbols of schema files and of code apart by module, which is the unit of their ids. */
internal object RoomModules {
    fun of(symbol: Symbol): String? = (symbol.id.identity() as? ParsedSymbolId.Valid)?.identity?.unit

    /** The [candidates] of [module], or all of them when none is: a schema directory may lie outside its module. */
    fun prefer(candidates: List<Symbol>, module: String?): List<Symbol> =
        candidates.filter { of(it) == module }.ifEmpty { candidates }
}
