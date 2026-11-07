package io.github.krosstack.kscope.semantic.worker

import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import kotlinx.serialization.Serializable

@Serializable
internal data class ModelDescriptor(
    val name: String,
    val revision: String,
    val dimensions: Int,
    val quantization: String,
    val pooling: String,
    val prefix: String,
    val maxTokens: Int,
    val files: Map<String, String>,
    val chunkBuilderVersion: Int = 1,
) {
    val identity: ModelIdentity get() = ModelIdentity(name, revision, dimensions, quantization, chunkBuilderVersion)
}
