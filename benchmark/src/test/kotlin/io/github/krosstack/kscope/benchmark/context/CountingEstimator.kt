package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal class CountingEstimator : TokenEstimator {
    override val id = TokenizerId("test_characters")
    override fun count(text: String) = TokenCount(text.length)
    override fun truncate(text: String, maxTokens: TokenCount) = text.take(maxTokens.value)
}
