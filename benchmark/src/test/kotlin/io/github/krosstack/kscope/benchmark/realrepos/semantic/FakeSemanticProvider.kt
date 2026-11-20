package io.github.krosstack.kscope.benchmark.realrepos.semantic

import io.github.krosstack.kscope.spi.embedding.EmbeddingProvider
import io.github.krosstack.kscope.spi.embedding.ModelIdentity

internal class FakeSemanticProvider(revision: String = "one") : EmbeddingProvider {
    override val id = "fake"
    override val identity = ModelIdentity("fake", revision, 2, "test", 1)
    val embedded = mutableListOf<String>()

    override fun cap(text: String, maxTokens: Int): String = text.take(maxTokens)

    override fun embed(texts: List<String>): List<FloatArray> {
        embedded.addAll(texts)
        return texts.map { floatArrayOf(1f, 0f) }
    }

    override fun embedQuery(text: String): FloatArray = floatArrayOf(1f, 0f)
}
