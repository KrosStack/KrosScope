package io.github.krosstack.kscope.service.context

import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.RetrievalStage
import io.github.krosstack.kscope.spi.context.TokenEstimator

public data class ContextRequest(
    public val task: String,
    public val budget: TokenCount? = null,
    public val level: ContextLevel = ContextLevel.RECOMMENDED,
    public val estimator: TokenEstimator = TokenEstimators.create(),
    public val stage: RetrievalStage = RetrievalStage.FULL,
    public val ml: MlMode = MlMode.ON,
    public val within: RepositoryId? = null,
)
