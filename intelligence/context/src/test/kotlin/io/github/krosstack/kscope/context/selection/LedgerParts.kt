package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.model.context.TokenCount

internal fun artifactNamed(path: String): ArtifactRef = ArtifactRef(RepositoryId("sample"), RepoPath(path))

internal fun fragmentOf(
    artifact: ArtifactRef,
    rendering: ContextRendering,
    relevance: Double,
    node: NodeRef = NodeRef.OfArtifact(artifact),
) = ContextFragment(artifact, null, node, rendering, ContextRole.READ, relevance, TokenCount(1), "text", false)

internal fun renderedFile(vararg values: Double) = RenderedFile(emptyList(), values, 0L, 0)
