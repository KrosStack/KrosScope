package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.spi.embedding.EmbeddingProvider
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import java.io.IOException
import java.security.MessageDigest

internal class RecordingEmbedding : EmbeddingProvider {
    override val id = "recording"
    override val identity = ModelIdentity("seeded-hash", "1", 8, "symmetric-int8", 1)
    val embedded = mutableListOf<String>()
    var capCalls = 0
    var failEmbedding = false

    override fun cap(text: String, maxTokens: Int): String {
        capCalls++
        val next = Regex("\\S+").findAll(text).drop(maxTokens).firstOrNull()
        return if (next == null) text else text.take(next.range.first)
    }

    override fun embed(texts: List<String>): List<FloatArray> {
        if (failEmbedding) throw IOException("Worker unavailable")
        embedded += texts
        return texts.map(::embedQuery)
    }

    override fun embedQuery(text: String): FloatArray {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return FloatArray(identity.dimensions) { digest[it].toFloat() / 127 }
    }
}
