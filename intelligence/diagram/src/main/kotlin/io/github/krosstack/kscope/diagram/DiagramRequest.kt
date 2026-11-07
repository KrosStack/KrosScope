package io.github.krosstack.kscope.diagram

public data class DiagramRequest(
    public val kind: DiagramKind,
    public val node: String? = null,
    public val depth: Int = 1,
    public val links: Int = 300,
    public val characters: Int = 40_000,
)
