package io.github.krosstack.kscope.spi.embedding

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public sealed interface EmbeddingResponse {
    @Serializable
    @SerialName("ready")
    public data class Ready(public val protocolVersion: Int, public val identity: ModelIdentity) : EmbeddingResponse

    @Serializable
    @SerialName("vectors")
    public data class Vectors(public val requestId: String, public val vectors: List<FloatArray>) : EmbeddingResponse

    @Serializable
    @SerialName("capped")
    public data class Capped(public val requestId: String, public val text: String) : EmbeddingResponse

    @Serializable
    @SerialName("failed")
    public data class Failed(public val requestId: String?, public val reason: String) : EmbeddingResponse
}
