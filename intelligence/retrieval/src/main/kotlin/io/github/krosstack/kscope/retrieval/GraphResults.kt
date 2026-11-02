package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class GraphScope {
    @SerialName("modules")
    MODULES,

    @SerialName("workspace")
    WORKSPACE,

    @SerialName("neighborhood")
    NEIGHBORHOOD,
}

@Serializable
public data class GraphNode(
    public val ref: NodeRef,
    public val label: String,
    public val artifact: ArtifactRef? = null,
)

@Serializable
public data class GraphResult(
    public val scope: GraphScope,
    public val nodes: List<GraphNode>,
    public val relationships: List<Relationship>,
    public val truncated: Boolean,
)

@Serializable
public data class RelatedResult(
    public val requested: String,
    public val roots: List<NodeRef>,
    public val files: List<RankedFile>,
    public val symbols: List<RankedSymbol>,
    public val relationships: List<Relationship>,
    public val truncated: Boolean,
)
