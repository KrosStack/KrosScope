package io.github.krosstack.kscope.context

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingType
import io.github.krosstack.kscope.context.token.BlockAdditive
import io.github.krosstack.kscope.context.token.BpeBlockBoundary
import io.github.krosstack.kscope.context.token.ExactMarkdownTokens
import io.github.krosstack.kscope.context.token.UnicodePrefix
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.spi.context.TokenEstimator

public object TokenEstimators {
    public val supported: List<TokenizerId> = listOf(
        TokenizerId("o200k_base"),
        TokenizerId("cl100k_base"),
        TokenizerId("chars_4"),
    )

    private val registry by lazy(Encodings::newLazyEncodingRegistry)
    private val o200k by lazy { BpeEstimator(registry.getEncoding(EncodingType.O200K_BASE)) }
    private val cl100k by lazy { BpeEstimator(registry.getEncoding(EncodingType.CL100K_BASE)) }
    private val characters = CharacterTokenEstimator()

    public fun create(id: TokenizerId = TokenizerId("o200k_base")): TokenEstimator = when (id.value) {
        "o200k_base" -> o200k
        "cl100k_base" -> cl100k
        "chars", "chars_4" -> characters
        else -> throw IllegalArgumentException("Unknown tokenizer: ${id.value}")
    }
}

private class BpeEstimator(private val encoding: Encoding) :
    TokenEstimator,
    BlockAdditive {
    override val id: TokenizerId = TokenizerId(encoding.name)
    private val blocks = ExactMarkdownTokens(this, maxEntries = MAX_BLOCK_ENTRIES)

    override fun count(text: String): TokenCount = TokenCount(encoding.countTokensOrdinary(text))

    override fun count(parts: List<String>): TokenCount = if (BpeBlockBoundary.safe(parts)) {
        TokenCount(parts.sumOf { blocks.count(it).value })
    } else {
        count(parts.joinToString(""))
    }

    override fun measure(block: String): Long = blocks.count(block).value.toLong()

    override fun tokens(totalMeasure: Long): Int = totalMeasure.toInt()

    override fun truncate(text: String, maxTokens: TokenCount): String = UnicodePrefix.truncate(text, maxTokens, this)

    private companion object {
        const val MAX_BLOCK_ENTRIES = 4096
    }
}

private class CharacterTokenEstimator :
    TokenEstimator,
    BlockAdditive {
    override val id: TokenizerId = TokenizerId("chars_4")

    override fun count(text: String): TokenCount = TokenCount((text.codePointCount(0, text.length) + CHARS - 1) / CHARS)

    override fun measure(block: String): Long = block.codePointCount(0, block.length).toLong()

    override fun tokens(totalMeasure: Long): Int = ((totalMeasure + CHARS - 1) / CHARS).toInt()

    override fun truncate(text: String, maxTokens: TokenCount): String = UnicodePrefix.truncate(text, maxTokens, this)

    private companion object {
        const val CHARS = 4
    }
}
