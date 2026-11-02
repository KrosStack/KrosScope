package io.github.krosstack.kscope.context.token

import io.github.krosstack.kscope.context.TokenCountKey
import io.github.krosstack.kscope.context.TokenCountStore
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal class TokenCosts(
    private val estimator: TokenEstimator,
    private val store: TokenCountStore = TokenCountStore.NONE,
) : FragmentCosts {
    private val cache = mutableMapOf<TokenCostKey, TokenCount>()

    override fun count(hash: ContentHash, range: TextRange?, rendering: ContextRendering, text: String): TokenCount =
        cache.getOrPut(TokenCostKey(hash, range, rendering, estimator.id, text)) {
            val key = TokenCountKey(hash, range, rendering, estimator.id, TextDigest.of(text))
            store.recall(key) ?: estimator.count(text).also { store.remember(key, it) }
        }
}

private data class TokenCostKey(
    val hash: ContentHash,
    val range: TextRange?,
    val rendering: ContextRendering,
    val estimator: TokenizerId,
    val text: String,
)
