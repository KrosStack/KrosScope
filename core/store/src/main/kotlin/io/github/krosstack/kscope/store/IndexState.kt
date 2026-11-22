package io.github.krosstack.kscope.store

public sealed interface IndexState {
    public data object Absent : IndexState

    public data class OtherFormat(public val found: Int, public val expected: Int) : IndexState

    public data class Damaged(public val reason: String) : IndexState

    public data object Readable : IndexState
}
