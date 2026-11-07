package io.github.krosstack.kscope.diagram

import kotlinx.serialization.Serializable

@Serializable
public data class Omission(public val nodes: Int, public val linksByState: Map<DrawnState, Int>)
