package io.github.krosstack.kscope.spi.embedding

public interface EmbeddingProvider {
    public val id: String
    public val identity: ModelIdentity

    public fun cap(text: String, maxTokens: Int): String

    public fun embed(texts: List<String>): List<FloatArray>

    public fun embedQuery(text: String): FloatArray
}
