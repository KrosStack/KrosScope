package io.github.krosstack.kscope.service.context

import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.retrieval.SemanticStatus

public data class ContextResult(public val context: ContextPackage, public val semantic: SemanticStatus)
