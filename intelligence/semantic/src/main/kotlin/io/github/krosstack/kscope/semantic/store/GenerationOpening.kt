package io.github.krosstack.kscope.semantic.store

public sealed interface GenerationOpening {
    public data class Opened(public val lease: GenerationLease, public val name: String) : GenerationOpening
    public data object Absent : GenerationOpening
    public data object Busy : GenerationOpening
    public data class Unavailable(public val reason: String) : GenerationOpening
}
