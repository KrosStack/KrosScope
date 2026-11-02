package io.github.krosstack.kscope.context.token

import io.github.krosstack.kscope.context.TokenCountKey
import io.github.krosstack.kscope.context.TokenCountStore
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.spi.context.TokenEstimator
import java.security.MessageDigest
import java.util.HexFormat

/** Counts the tokens of a whole rendered package; a long one is counted once and then recalled from the store. */
internal class PackageTokens(val estimator: TokenEstimator, private val store: TokenCountStore) {
    fun count(text: String): TokenCount {
        if (text.length < MINIMUM_CHARACTERS) return estimator.count(text)
        val digest = TextDigest.of(text)
        val key = TokenCountKey(ContentHash(digest), null, null, estimator.id, digest)
        return store.recall(key) ?: estimator.count(text).also { store.remember(key, it) }
    }

    private companion object {
        const val MINIMUM_CHARACTERS = 4_096
    }
}

internal object TextDigest {
    fun of(text: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
}
