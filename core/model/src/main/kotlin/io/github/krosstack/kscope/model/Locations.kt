package io.github.krosstack.kscope.model

import kotlinx.serialization.Serializable

@Serializable
public data class ArtifactRef(public val repository: RepositoryId, public val path: RepoPath)

/** Lines and columns are 1-based; the end position is exclusive. */
@Serializable
public data class TextRange(
    public val startLine: Int,
    public val startColumn: Int,
    public val endLine: Int,
    public val endColumn: Int,
)

@Serializable
public data class SourceLocation(public val artifact: ArtifactRef, public val range: TextRange)
