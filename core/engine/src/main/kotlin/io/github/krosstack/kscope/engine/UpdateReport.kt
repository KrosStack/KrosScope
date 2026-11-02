package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [stageMillis] and nothing else in a report depends on timing; reports are never part of the graph. */
@Serializable
public data class UpdateReport(
    public val unitsProduced: Int,
    public val unitsSkipped: Int,
    public val unitsRemoved: Int,
    public val artifacts: Int,
    public val diagnostics: List<Diagnostic>,
    public val verify: VerifyReport?,
    public val stageMillis: Map<String, Long>,
)

@Serializable
public enum class DifferenceKind {
    @SerialName("missing-from-incremental")
    MISSING_FROM_INCREMENTAL,

    @SerialName("unexpected-in-incremental")
    UNEXPECTED_IN_INCREMENTAL,

    @SerialName("different-content")
    DIFFERENT_CONTENT,
}

@Serializable
public data class UnitDifference(
    public val producer: ProducerRef,
    public val owner: OwnershipUnit,
    public val kind: DifferenceKind,
)

/**
 * [differences] lists the first differing units in canonical order; [differingUnits] counts all of them.
 * [differingSearchDocuments] counts the documents of the search index that are missing, unexpected or different.
 */
@Serializable
public data class VerifyReport(
    public val matches: Boolean,
    public val differingUnits: Int,
    public val differences: List<UnitDifference>,
    public val differingSearchDocuments: Int = 0,
)
