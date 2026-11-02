package io.github.krosstack.kscope.spi.context

import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId

public interface TokenEstimator {
    public val id: TokenizerId

    public fun count(text: String): TokenCount

    /** Counts the exact concatenation; supplied boundaries do not separate tokens. */
    public fun count(parts: List<String>): TokenCount = count(parts.joinToString(""))

    public fun truncate(text: String, maxTokens: TokenCount): String
}
