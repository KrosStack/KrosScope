package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.token.BlockAdditive
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal class AdditiveCountingEstimator(private val delegate: TokenEstimator) :
    TokenEstimator,
    BlockAdditive {
    private val additive = delegate as BlockAdditive
    private var blocks = 0
    private var texts = 0
    private var packages = 0

    override val id: TokenizerId = delegate.id

    override fun count(text: String): TokenCount {
        texts++
        return delegate.count(text)
    }

    override fun count(parts: List<String>): TokenCount {
        packages++
        return delegate.count(parts)
    }

    override fun measure(block: String): Long {
        blocks++
        return additive.measure(block)
    }

    override fun tokens(totalMeasure: Long): Int = additive.tokens(totalMeasure)

    override fun truncate(text: String, maxTokens: TokenCount): String = delegate.truncate(text, maxTokens)

    fun snapshot(): Work = Work(blocks + texts, packages)

    data class Work(val total: Int, val packages: Int) {
        operator fun minus(other: Work) = Work(total - other.total, packages - other.packages)
    }
}
