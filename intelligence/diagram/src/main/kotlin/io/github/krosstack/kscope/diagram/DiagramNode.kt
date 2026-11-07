package io.github.krosstack.kscope.diagram

import kotlinx.serialization.Serializable

@Serializable
public data class DiagramNode(
    public val id: String,
    public val label: String,
    public val kind: String,
    public val group: String?,
)
