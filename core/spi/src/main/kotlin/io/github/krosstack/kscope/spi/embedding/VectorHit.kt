package io.github.krosstack.kscope.spi.embedding

import io.github.krosstack.kscope.model.NodeRef

public data class VectorHit(public val node: NodeRef, public val score: Double)
