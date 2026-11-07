package io.github.krosstack.kscope.semantic

import io.github.krosstack.kscope.semantic.store.PublishResult

public data class IndexingReport(
    public val embedded: Int,
    public val reused: Int,
    public val dropped: Int,
    public val behind: Int,
    public val publication: PublishResult,
)
