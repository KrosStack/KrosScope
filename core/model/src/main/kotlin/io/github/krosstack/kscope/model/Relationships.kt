package io.github.krosstack.kscope.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class RelationKind {
    @SerialName("contains")
    CONTAINS,

    @SerialName("references")
    REFERENCES,

    @SerialName("calls")
    CALLS,

    @SerialName("extends")
    EXTENDS,

    @SerialName("implements")
    IMPLEMENTS,

    @SerialName("overrides")
    OVERRIDES,

    @SerialName("imports")
    IMPORTS,

    @SerialName("type-uses")
    TYPE_USES,

    /** From an `actual` declaration to its `expect` declaration. */
    @SerialName("actualizes")
    ACTUALIZES,

    /** From a source of truth to the generated surface derived from it. */
    @SerialName("generates")
    GENERATES,

    /** From a contract endpoint to its implementation or consumer across an artifact boundary. */
    @SerialName("binds")
    BINDS,

    /** Between modules or between source sets. */
    @SerialName("depends-on")
    DEPENDS_ON,

    @SerialName("tests")
    TESTS,

    @SerialName("co-changes-with")
    CO_CHANGES_WITH,
}

/**
 * [site] is the occurrence: where inside [from] the relationship is expressed. [attributes] are producer-specific
 * and serialized sorted by key.
 */
@Serializable
public data class Relationship(
    public val from: NodeRef,
    public val kind: RelationKind,
    public val subKind: String?,
    public val resolution: Resolution,
    public val evidenceClass: EvidenceClass,
    public val site: SourceLocation?,
    public val scope: Scope,
    @Serializable(with = SortedAttributesSerializer::class)
    public val attributes: Map<String, String> = emptyMap(),
)
