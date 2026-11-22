package io.github.krosstack.kscope.semantic.worker

import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import kotlinx.serialization.Serializable
import java.security.MessageDigest

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

    fun pipelineIdentity(device: String, descriptorText: String): ModelIdentity {
        if (device == "cpu" && quantization !in setOf("fp16", "fp32")) return identity
        val recipe = "$descriptorText\nort=1.31.1;threads=2,1;batch=16,1;device=$device"
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(recipe.toByteArray(Charsets.UTF_8)).toHexString()
        return identity.copy(revision = "$revision;pipeline-v1:$device:$quantization:$fingerprint")
    }
}
