package io.github.krosstack.kscope.spi.embedding

import kotlinx.serialization.Serializable

@Serializable
public data class ModelIdentity(
    public val name: String,
    public val revision: String,
    public val dimensions: Int,
    public val quantization: String,
    public val chunkBuilderVersion: Int,
)
