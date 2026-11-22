package io.github.krosstack.kscope.benchmark.context.identity

import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.RetrievalStage
import kotlinx.serialization.Serializable

@Serializable
public data class ContextAnalysisKey(
    public val stage: RetrievalStage,
    public val ml: MlMode,
    public val pipeline: ContentHash?,
) {
    init {
        require((ml == MlMode.ON) == (pipeline != null)) { "Only ON analysis requires a pipeline fingerprint." }
    }
}
