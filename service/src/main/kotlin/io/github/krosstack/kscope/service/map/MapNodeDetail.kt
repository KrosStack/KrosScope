package io.github.krosstack.kscope.service.map

import kotlinx.serialization.Serializable

@Serializable
public data class MapSymbol(
    public val id: String,
    public val name: String,
    public val kind: String,
    public val category: String,
    public val container: String? = null,
    public val line: Int? = null,
    public val endLine: Int? = null,
    public val signature: String? = null,
    public val visibility: String? = null,
    public val flags: List<String> = emptyList(),
    public val generated: Boolean = false,
)

@Serializable
public data class MapLink(
    public val kind: String,
    public val subKind: String? = null,
    public val certainty: String,
    public val evidence: String,
    public val line: Int? = null,
    public val sitePath: String? = null,
    public val fromSymbol: String? = null,
    public val toSymbol: String? = null,
    public val fromNode: Int,
    public val toNode: Int,
    public val fromLabel: String,
    public val toLabel: String,
    public val attributes: Map<String, String> = emptyMap(),
)

/** A relationship that leaves the node and has no target; [state] is `unresolved` or `not-analysed`. */
@Serializable
public data class MapOpenLink(
    public val fromSymbol: String? = null,
    public val fromLabel: String,
    public val kind: String,
    public val state: String,
    public val hint: String,
    public val reason: String,
    public val line: Int? = null,
)

/** What is attached directly to one node. [truncated] says that a list was cut at its limit. */
@Serializable
public data class MapNodeDetail(
    public val id: String,
    public val symbols: List<MapSymbol>,
    public val outgoing: List<MapLink>,
    public val incoming: List<MapLink>,
    public val open: List<MapOpenLink>,
    public val truncated: Boolean = false,
)
