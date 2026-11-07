package io.github.krosstack.kscope.service.components

public sealed interface RemoveResult {
    public data object Removed : RemoveResult
    public data object NotInstalled : RemoveResult
    public data class Refused(public val reason: String) : RemoveResult
}
