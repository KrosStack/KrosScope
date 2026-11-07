package io.github.krosstack.kscope.semantic.chunk

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef

public data class Chunk(
    public val key: String,
    public val kind: ChunkKind,
    public val text: String,
    public val nodes: List<NodeRef>,
    public val artifact: ArtifactRef,
)
