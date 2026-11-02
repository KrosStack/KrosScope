package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.AnalyzerCapability

internal object WorkerIdentity {
    val producer = ProducerRef("kscope.analyzer.kotlin", "0.1.0")
    val capabilities = setOf(
        AnalyzerCapability.DECLARATIONS,
        AnalyzerCapability.RESOLVED_REFERENCES,
        AnalyzerCapability.CALLS,
        AnalyzerCapability.INHERITANCE,
        AnalyzerCapability.SCRIPT_OUTLINE,
        AnalyzerCapability.API_DIGEST,
    )
}
