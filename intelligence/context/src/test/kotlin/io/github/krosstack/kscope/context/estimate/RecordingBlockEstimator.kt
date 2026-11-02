package io.github.krosstack.kscope.context.estimate

import io.github.krosstack.kscope.context.token.BlockAdditive
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal class RecordingBlockEstimator(private val delegate: TokenEstimator) :
    TokenEstimator,
    BlockAdditive {
    private val additive = delegate as BlockAdditive
    val counted = mutableListOf<String>()
    val measured = mutableListOf<String>()

    override val id: TokenizerId = delegate.id

    override fun count(text: String): TokenCount {
        counted += text
        return delegate.count(text)
    }

    override fun count(parts: List<String>): TokenCount {
        counted += parts.joinToString("")
        return delegate.count(parts)
    }

    override fun measure(block: String): Long {
        measured += block
        return additive.measure(block)
    }

    override fun tokens(totalMeasure: Long): Int = additive.tokens(totalMeasure)

    override fun truncate(text: String, maxTokens: TokenCount): String = delegate.truncate(text, maxTokens)
}
