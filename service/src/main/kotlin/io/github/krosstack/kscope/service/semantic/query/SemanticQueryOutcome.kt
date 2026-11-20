package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.retrieval.SemanticStatus

internal sealed interface SemanticQueryOutcome {
    data class Applied(
        val files: List<ArtifactRef>,
        val status: SemanticStatus,
        val snapshotIdentity: CurrentSemanticGeneration.Identity,
        val behindChunks: Int,
    ) : SemanticQueryOutcome

    data class Skipped(val status: SemanticStatus, val reason: Reason, val detail: String = "") : SemanticQueryOutcome

    enum class Reason {
        OFF,
        NOT_INSTALLED,
        DISABLED,
        UNPROVEN_INVENTORY,
        UNAVAILABLE_GENERATION,
        ADMISSION_BUSY,
        MEMORY_BUSY,
        STARTUP_FAILED,
        WORKER_FAILED,
        WORKER_TIMED_OUT,
        DEADLINE_EXPIRED,
        SUPERSEDED,
    }
}
