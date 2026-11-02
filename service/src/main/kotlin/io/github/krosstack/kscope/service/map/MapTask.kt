package io.github.krosstack.kscope.service.map

import kotlinx.serialization.Serializable

@Serializable
public data class MapRankedFile(
    public val node: Int,
    public val score: Double,
    public val role: String,
    public val reasons: List<String>,
)

@Serializable
public data class MapRankedSymbol(
    public val node: Int,
    public val id: String,
    public val name: String,
    public val score: Double,
)

@Serializable
public data class MapRankedModule(public val node: Int, public val score: Double)

/** What the ranking of a task points at, in positions of the tree. */
@Serializable
public data class MapTask(
    public val query: String,
    public val terms: List<String>,
    public val confidence: String,
    public val files: List<MapRankedFile>,
    public val tests: List<MapRankedFile>,
    public val symbols: List<MapRankedSymbol>,
    public val modules: List<MapRankedModule>,
)
