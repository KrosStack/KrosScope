package io.github.krosstack.kscope.benchmark.graph

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.model.NodeRef

/** Why a gold address could not be mapped to a node of the graph. */
public enum class UnmappedReason(public val code: String) {
    INCOMPLETE_ADDRESS("incomplete-address"),
    FILE_NOT_INDEXED("file-not-indexed"),
    NO_SYMBOL_IN_FILE("no-symbol-in-file"),
    NO_MATCHING_SYMBOL("no-matching-symbol"),
    AMBIGUOUS("ambiguous"),
    GENERATED_SURFACE_NOT_PRODUCED("generated-surface-not-produced"),
    MODULE_NOT_FOUND("module-not-found"),
    KIND_NOT_SUPPORTED("kind-not-supported-yet"),
}

/** The result of mapping a gold address. */
public sealed interface AddressResolution {
    /** [approximateLine] is true when the symbol was the only candidate but its line differs from the address. */
    public data class Found(public val node: NodeRef, public val approximateLine: Boolean = false) : AddressResolution

    public data class Unmapped(public val address: GoldAddress, public val reason: UnmappedReason) : AddressResolution {
        public fun describe(): String = "${reason.code}: ${address.describe()}"
    }
}
