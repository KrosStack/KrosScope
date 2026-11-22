package io.github.krosstack.kscope.service.context

import io.github.krosstack.kscope.retrieval.AnalysisResult

internal data class RankedAnalysis(val result: AnalysisResult, val structural: AnalysisResult)
