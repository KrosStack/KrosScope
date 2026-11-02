package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal class CountingTokenEstimator(private val delegate: TokenEstimator) : TokenEstimator {
    override val id: TokenizerId = delegate.id
    var calls: Int = 0
        private set

    override fun count(text: String): TokenCount {
        calls++
        return delegate.count(text)
    }

    override fun truncate(text: String, maxTokens: TokenCount): String = delegate.truncate(text, maxTokens)
}
