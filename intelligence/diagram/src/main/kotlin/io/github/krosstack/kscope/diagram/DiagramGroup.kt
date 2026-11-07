package io.github.krosstack.kscope.diagram

import kotlinx.serialization.Serializable

@Serializable
public data class DiagramGroup(public val id: String, public val label: String, public val members: List<String>)
