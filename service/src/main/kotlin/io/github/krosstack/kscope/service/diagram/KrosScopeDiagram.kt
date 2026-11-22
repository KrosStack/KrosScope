package io.github.krosstack.kscope.service.diagram

import io.github.krosstack.kscope.diagram.DiagramBuilder
import io.github.krosstack.kscope.diagram.DiagramKind
import io.github.krosstack.kscope.diagram.DiagramRequest
import io.github.krosstack.kscope.diagram.DiagramResult
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.QueryStages
import io.github.krosstack.kscope.service.stage
import io.github.krosstack.kscope.spi.graph.GraphReader

/**
 * Draws the diagram that [request] asks for from the index. A module or repository that the index does not hold is
 * [DiagramResult.NotFound], like a symbol that it does not hold.
 */
public fun KrosScope.diagram(request: DiagramRequest): DiagramResult {
    memory.load()
    return progress.stage(QueryStages.DIAGRAM) {
        val reader = graph()
        when {
            reader.holdsNothingNamed(request) -> DiagramResult.NotFound(request.node)
            else -> DiagramBuilder(reader).build(request)
        }
    }
}

private fun GraphReader.holdsNothingNamed(request: DiagramRequest): Boolean {
    val node = request.node ?: return false
    val structure = structure()
    return when (request.kind) {
        DiagramKind.MODULES -> structure.modules.none { node == it.id.value || node == it.path }
        DiagramKind.COMPONENTS -> structure.repositories.none { node == it.id.value || node == it.name }
        DiagramKind.SYMBOL -> false
    }
}
