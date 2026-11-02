package io.github.krosstack.kscope.model

import kotlinx.serialization.Serializable

/**
 * A reference that a producer saw but could not look up, because it reads one file at a time: [key] names the target
 * in the [namespace] of the capability that can find it, such as `string/app_name` in `android.resource`. It is an
 * input for the boundary linker that claims the namespace and never an edge: the linker answers each reference with
 * exactly one [Relationship] built by [answeredBy].
 *
 * [from], [kind], [subKind], [site] and [scope] are the ones the answering relationship carries.
 */
@Serializable
public data class SymbolicReference(
    public val from: NodeRef,
    public val kind: RelationKind,
    public val subKind: String?,
    public val namespace: String,
    public val key: String,
    public val site: SourceLocation?,
    public val scope: Scope,
    public val evidenceClass: EvidenceClass,
) {
    /** The relationship that answers this reference with [resolution]. */
    public fun answeredBy(
        resolution: Resolution,
        evidence: EvidenceClass = evidenceClass,
        attributes: Map<String, String> = emptyMap(),
    ): Relationship = Relationship(from, kind, subKind, resolution, evidence, site, scope, attributes)

    /** Equal for a reference and for every relationship that answers it. */
    public fun answerKey(): AnswerKey = AnswerKey(from, kind, subKind, site, scope)
}

/** What a [SymbolicReference] and the [Relationship] that answers it have in common. */
public data class AnswerKey(
    public val from: NodeRef,
    public val kind: RelationKind,
    public val subKind: String?,
    public val site: SourceLocation?,
    public val scope: Scope,
)

public fun Relationship.answerKey(): AnswerKey = AnswerKey(from, kind, subKind, site, scope)
