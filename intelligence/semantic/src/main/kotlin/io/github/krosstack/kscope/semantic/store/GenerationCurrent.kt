package io.github.krosstack.kscope.semantic.store

public sealed interface GenerationCurrent {
    public data class Current(public val name: String) : GenerationCurrent
    public data object Absent : GenerationCurrent
    public data object Busy : GenerationCurrent
    public data class Unavailable(public val reason: String) : GenerationCurrent
}
