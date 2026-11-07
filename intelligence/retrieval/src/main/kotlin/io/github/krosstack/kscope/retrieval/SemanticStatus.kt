package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import kotlinx.serialization.Serializable

@Serializable
public data class SemanticStatus(
    public val state: SemanticState,
    public val provider: String? = null,
    public val model: ModelIdentity? = null,
    public val behindChunks: Int = 0,
) {
    public companion object {
        public val OFF: SemanticStatus = SemanticStatus(SemanticState.OFF)
        public val NOT_INSTALLED: SemanticStatus = SemanticStatus(SemanticState.NOT_INSTALLED)
    }
}
