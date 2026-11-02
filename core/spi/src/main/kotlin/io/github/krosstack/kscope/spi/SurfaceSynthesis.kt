package io.github.krosstack.kscope.spi

import io.github.krosstack.kscope.model.SourceSetId
import kotlinx.serialization.Serializable

/** A signature-only stub handed to the semantic analyzer in place of generated code. */
@Serializable
public data class SyntheticSource(
    public val virtualPath: String,
    public val language: String,
    public val text: String,
    public val sourceSet: SourceSetId,
    public val generator: String,
)

public interface SurfaceSynthesizer {
    public val descriptor: ProducerDescriptor

    public fun synthesize(request: ProduceRequest, sink: FactSink): List<SyntheticSource>
}
