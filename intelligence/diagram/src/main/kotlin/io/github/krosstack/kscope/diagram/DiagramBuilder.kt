package io.github.krosstack.kscope.diagram

import io.github.krosstack.kscope.diagram.build.DiagramCut
import io.github.krosstack.kscope.diagram.build.DiagramQuery
import io.github.krosstack.kscope.diagram.build.ModulesDiagramBuilder
import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import io.github.krosstack.kscope.spi.graph.GraphReader

public class DiagramBuilder(private val reader: GraphReader) {
    public fun build(request: DiagramRequest): DiagramResult {
        val diagram = when (request.kind) {
            DiagramKind.MODULES -> ModulesDiagramBuilder().build(reader, request)
            DiagramKind.COMPONENTS -> DiagramQuery(reader, DiagramKind.COMPONENTS).build(request)
        }
        return DiagramCut().apply(diagram, request) { MermaidEmitter.render(it).length }
    }
}
