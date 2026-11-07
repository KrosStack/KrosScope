package io.github.krosstack.kscope.diagram

import kotlinx.serialization.Serializable

@Serializable
public data class DiagramEdge(
    public val from: String,
    public val to: String,
    public val label: String,
    public val weakest: DrawnState,
    public val byState: Map<DrawnState, Int>,
    public val byEvidence: Map<String, Int>,
)
