package io.github.krosstack.kscope.benchmark.context.identity

import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
public data class ContextPipelineIdentity(
    public val provider: String,
    public val model: ModelIdentity,
    public val implementation: ContentHash,
    public val workerFiles: Map<String, ContentHash>,
    public val modelFiles: Map<String, ContentHash>,
    public val runtime: Map<String, String>,
    public val policy: Map<String, String>,
    public val generation: ContentHash? = null,
) {
    public fun fingerprint(): ContentHash = ContextBenchmarkIdentity.fingerprint(KscopeJson.encodeToJsonElement(this))
}
