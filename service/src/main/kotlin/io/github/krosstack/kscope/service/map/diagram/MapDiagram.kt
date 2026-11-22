package io.github.krosstack.kscope.service.map.diagram

import io.github.krosstack.kscope.diagram.Diagram
import kotlinx.serialization.Serializable

/**
 * The answer to a diagram question: the [diagram] as the diagram module draws it, and in [files], for each symbol id
 * that a node of it carries, the id of the node of its file in the map. A symbol without a file in the map has no
 * entry. A question that has no answer returns a value with [error].
 */
@Serializable
public data class MapDiagram(
    public val diagram: Diagram? = null,
    public val files: Map<String, String> = emptyMap(),
    public val error: String? = null,
)
