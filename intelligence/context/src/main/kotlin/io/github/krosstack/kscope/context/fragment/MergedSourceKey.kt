package io.github.krosstack.kscope.context.fragment

import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextRendering

internal data class MergedSourceKey(
    val document: SourceDocument,
    val hash: ContentHash,
    val range: TextRange,
    val rendering: ContextRendering,
)
