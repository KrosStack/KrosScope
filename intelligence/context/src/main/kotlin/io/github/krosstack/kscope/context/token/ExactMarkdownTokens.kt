package io.github.krosstack.kscope.context.token

import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.spi.context.TokenEstimator
import java.util.LinkedHashMap

internal class ExactMarkdownTokens(
    private val estimator: TokenEstimator,
    private val maxCharacters: Int = MAX_CHARACTERS,
    private val maxEntries: Int = MAX_ENTRIES,
) {
    private val counts = LinkedHashMap<MarkdownTokenKey, TokenCount>(INITIAL_CAPACITY, LOAD_FACTOR, true)
    private var characters = 0

    fun count(text: String): TokenCount = exact(text) { estimator.count(text) }

    fun count(parts: List<String>): TokenCount = exact(parts.joinToString("")) { estimator.count(parts) }

    @Synchronized
    private fun exact(text: String, compute: () -> TokenCount): TokenCount {
        val key = MarkdownTokenKey(estimator.id, text)
        val cached = counts[key]
        if (cached != null) return cached
        val value = compute()
        if (text.length <= maxCharacters) {
            counts[key] = value
            characters += text.length
            evict()
        }
        return value
    }

    private fun evict() {
        while (counts.size > maxEntries || characters > maxCharacters) {
            val iterator = counts.entries.iterator()
            val entry = iterator.next()
            characters -= entry.key.text.length
            iterator.remove()
        }
    }

    private companion object {
        const val MAX_CHARACTERS = 4 * 1024 * 1024
        const val MAX_ENTRIES = 256
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}

private data class MarkdownTokenKey(val estimator: TokenizerId, val text: String)
