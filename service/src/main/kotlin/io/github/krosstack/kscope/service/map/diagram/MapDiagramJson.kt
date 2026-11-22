package io.github.krosstack.kscope.service.map.diagram

import kotlinx.serialization.json.Json

/** Renders the answer of a diagram question. The diagram inside keeps the fields that the diagram command prints. */
public object MapDiagramJson {
    public fun encode(answer: MapDiagram): String = Json.encodeToString(MapDiagram.serializer(), answer)
}
