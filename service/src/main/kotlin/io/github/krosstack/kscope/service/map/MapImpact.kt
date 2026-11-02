package io.github.krosstack.kscope.service.map

import kotlinx.serialization.Serializable

@Serializable
public data class MapImpactEntry(
    public val node: Int,
    public val symbol: String? = null,
    public val symbolId: String? = null,
    public val distance: Int,
    public val certainty: String,
)

/**
 * The answer to an impact question in positions of the tree. A question that cannot be answered carries [error] and
 * nothing else; [truncated] says that [affected] was cut at its limit, nearest first.
 */
@Serializable
public data class MapImpact(
    public val requested: String,
    public val roots: List<Int> = emptyList(),
    public val affected: List<MapImpactEntry> = emptyList(),
    public val tests: List<Int> = emptyList(),
    public val unresolved: Int = 0,
    public val truncated: Boolean = false,
    public val error: String? = null,
)
