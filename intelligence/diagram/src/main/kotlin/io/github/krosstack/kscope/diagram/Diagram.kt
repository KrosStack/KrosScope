package io.github.krosstack.kscope.diagram

import kotlinx.serialization.Serializable

/** Title counts refer to original scoped nodes; synthetic state summaries do not consume that count. */
@Serializable
public data class Diagram(
    public val kind: DiagramKind,
    public val title: String,
    public val nodes: List<DiagramNode>,
    public val groups: List<DiagramGroup>,
    public val edges: List<DiagramEdge>,
    public val reasons: List<ReasonNode>,
    public val omitted: Omission,
    public val legend: List<String>,
)
