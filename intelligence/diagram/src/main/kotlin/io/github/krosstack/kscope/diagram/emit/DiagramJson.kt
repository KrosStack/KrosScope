package io.github.krosstack.kscope.diagram.emit

import io.github.krosstack.kscope.diagram.Diagram
import kotlinx.serialization.json.Json

public object DiagramJson {
    private val json = Json { prettyPrint = true }

    public fun render(diagram: Diagram): String = json.encodeToString(Diagram.serializer(), diagram) + "\n"
}
