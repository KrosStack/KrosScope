package io.github.krosstack.kscope.semantic.store

import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import kotlinx.serialization.Serializable

@Serializable
public data class GenerationHeader(
    public val provider: String,
    public val identity: ModelIdentity,
    public val dimensions: Int,
    public val chunkCount: Int,
)
