package io.github.krosstack.kscope.semantic.store

public sealed interface PublishResult {
    public data class Published(public val generation: String) : PublishResult
    public data class Deferred(public val reason: String) : PublishResult
}
