package io.github.krosstack.kscope.context

import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal class CharacterEstimator : TokenEstimator {
    override val id: TokenizerId = TokenizerId("test_characters")

    override fun count(text: String): TokenCount = TokenCount(text.length)

    override fun truncate(text: String, maxTokens: TokenCount): String = text.take(maxTokens.value)
}
