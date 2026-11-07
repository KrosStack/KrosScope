package io.github.krosstack.kscope.semantic

import io.github.krosstack.kscope.spi.embedding.EmbeddingProvider
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import java.security.MessageDigest

internal class FakeEmbeddingProvider : EmbeddingProvider {
    override val id = "fake"
    override val identity = ModelIdentity("seeded-hash", "1", 8, "symmetric-int8", 1)
    val batches = mutableListOf<List<String>>()
    var failAfter = Int.MAX_VALUE

    override fun cap(text: String, maxTokens: Int): String {
        val next = Regex("\\S+").findAll(text).drop(maxTokens).firstOrNull()
        return if (next == null) text else text.take(next.range.first)
    }

    override fun embed(texts: List<String>): List<FloatArray> {
        check(batches.size < failAfter) { "Provider unavailable" }
        batches += texts
        return texts.map(::embedQuery)
    }

    override fun embedQuery(text: String): FloatArray {
        val digest = MessageDigest.getInstance("SHA-256").digest("seed:42:$text".toByteArray())
        return FloatArray(identity.dimensions) { digest[it].toFloat() / 127 }
    }
}
