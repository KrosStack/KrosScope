package io.github.krosstack.kscope.diagram

import io.github.krosstack.kscope.diagram.build.DiagramCut
import io.github.krosstack.kscope.diagram.build.DiagramQuery
import io.github.krosstack.kscope.diagram.build.ModulesDiagramBuilder
import io.github.krosstack.kscope.diagram.build.symbol.SymbolDiagramBuilder
import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import io.github.krosstack.kscope.spi.graph.GraphReader

public class DiagramBuilder(private val reader: GraphReader) {
    public fun build(request: DiagramRequest): DiagramResult = when (request.kind) {
        DiagramKind.MODULES -> cut(ModulesDiagramBuilder().build(reader, request), request)
        DiagramKind.COMPONENTS -> cut(DiagramQuery(reader, DiagramKind.COMPONENTS).build(request), request)
        DiagramKind.SYMBOL -> SymbolDiagramBuilder(reader).build(request) { cut(it, request) }
    }

    private fun cut(diagram: Diagram, request: DiagramRequest): DiagramResult =
        DiagramCut().apply(diagram, request) { MermaidEmitter.render(it).length }
}
