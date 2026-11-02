package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolEcosystem
import io.github.krosstack.kscope.model.SymbolOrigin

/** What two symbols share when they are the same declaration in different source sets: the module and descriptor. */
internal data class DeclarationKey(val module: String, val descriptor: String)

/** Null for a symbol of a library or an id that does not parse. */
internal fun Symbol.declarationKey(): DeclarationKey? {
    val identity = (id.identity() as? ParsedSymbolId.Valid)?.identity
    val isWorkspace = identity?.ecosystem == SymbolEcosystem.GRADLE
    return if (identity != null && isWorkspace) DeclarationKey(identity.unit, identity.descriptor.render()) else null
}

internal fun Symbol.isFromSource(): Boolean = origin is SymbolOrigin.Source
