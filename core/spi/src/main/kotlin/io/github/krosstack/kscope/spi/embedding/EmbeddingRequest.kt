package io.github.krosstack.kscope.spi.embedding

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public sealed interface EmbeddingRequest {
    @Serializable
    @SerialName("handshake")
    public data class Handshake(public val protocolVersion: Int) : EmbeddingRequest

    @Serializable
    @SerialName("embed")
    public data class Embed(
        public val requestId: String,
        public val texts: List<String>,
        public val query: Boolean = false,
    ) : EmbeddingRequest

    @Serializable
    @SerialName("cap")
    public data class Cap(public val requestId: String, public val text: String, public val maxTokens: Int) :
        EmbeddingRequest

    @Serializable
    @SerialName("cancel")
    public data class Cancel(public val requestId: String) : EmbeddingRequest

    @Serializable
    @SerialName("shutdown")
    public data object Shutdown : EmbeddingRequest
}
