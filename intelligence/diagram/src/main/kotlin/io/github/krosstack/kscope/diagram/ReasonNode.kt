package io.github.krosstack.kscope.diagram

import kotlinx.serialization.Serializable

@Serializable
public data class ReasonNode(public val id: String, public val reason: String, public val count: Int)
