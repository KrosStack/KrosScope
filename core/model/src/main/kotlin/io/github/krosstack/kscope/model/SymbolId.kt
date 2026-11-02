package io.github.krosstack.kscope.model

import kotlinx.serialization.Serializable

/** Wire form of a [SymbolIdentity]; build it with [SymbolIdentity.toSymbolId] and read it back with [parse]. */
@Serializable
@JvmInline
public value class SymbolId(public val value: String) {
    public fun identity(): ParsedSymbolId = parse(value)

    public companion object {
        public fun parse(text: String): ParsedSymbolId = SymbolIdParser.parse(text)
    }
}

public sealed interface ParsedSymbolId {
    public data class Valid(public val identity: SymbolIdentity) : ParsedSymbolId

    public data class Invalid(public val text: String, public val problem: String) : ParsedSymbolId
}
