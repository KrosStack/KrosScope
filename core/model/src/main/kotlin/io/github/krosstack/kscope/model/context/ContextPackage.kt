package io.github.krosstack.kscope.model.context

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.TextRange
import kotlinx.serialization.Serializable

@Serializable
public data class ContextCandidate(
    public val node: NodeRef,
    public val relevance: Double,
    public val role: ContextRole = ContextRole.READ,
)

@Serializable
public data class ContextInput(
    public val task: String,
    public val candidates: List<ContextCandidate>,
    public val boundaries: List<Relationship> = emptyList(),
    public val tests: List<NodeRef> = emptyList(),
    public val unresolved: List<Relationship> = emptyList(),
)

@Serializable
public data class ContextFragment(
    public val artifact: ArtifactRef,
    public val range: TextRange?,
    public val node: NodeRef,
    public val rendering: ContextRendering,
    public val role: ContextRole,
    public val relevance: Double,
    public val tokens: TokenCount,
    public val text: String,
    public val sourceBacked: Boolean,
    public val evidenceNote: String? = null,
)

@Serializable
public data class ContextOmission(public val node: NodeRef?, public val reason: ContextOmissionReason)

@Serializable
public data class ContextPackage(
    public val task: String,
    public val level: ContextLevel,
    public val budget: TokenCount?,
    public val estimator: TokenizerId,
    public val totalTokens: TokenCount,
    public val fragments: List<ContextFragment>,
    public val boundaries: List<Relationship> = emptyList(),
    public val tests: List<NodeRef> = emptyList(),
    public val unresolved: List<Relationship> = emptyList(),
    public val omitted: List<ContextOmission> = emptyList(),
    public val markdown: String = "",
    public val schemaVersion: Int = 1,
)
