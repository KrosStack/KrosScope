package io.github.krosstack.kscope.diagram

import io.github.krosstack.kscope.diagram.emit.DiagramJson
import io.github.krosstack.kscope.diagram.emit.DotEmitter
import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class ModulesDiagramGoldenTest {
    @Test
    fun `dependencies within one module produce no module edge or cycle`() {
        val fixture = DiagramGraph()
        val module = fixture.structure().modules.first().id
        val dependency = Relationship(
            from = NodeRef.OfSymbol(
                SymbolIdentity.workspace(module, null, SymbolDescriptor().term("input")).toSymbolId(),
            ),
            kind = RelationKind.DEPENDS_ON,
            subKind = "generator-input",
            resolution = Resolution.Resolved(
                NodeRef.OfSymbol(
                    SymbolIdentity.workspace(module, null, SymbolDescriptor().term("spec")).toSymbolId(),
                ),
                Certainty.EXACT,
            ),
            evidenceClass = EvidenceClass.BUILD_MODEL,
            site = null,
            scope = Scope(),
        )
        val graph = object : GraphReader by fixture {
            override fun relationships(query: RelationshipQuery): List<Relationship> = listOf(dependency)
                .filter { query.kinds.isEmpty() || it.kind in query.kinds }
        }
        val diagram = (
            DiagramBuilder(graph).build(DiagramRequest(DiagramKind.MODULES)) as DiagramResult.Built
            ).diagram
        assertEquals(
            emptyList<DiagramEdge>() to Omission(0, DrawnState.entries.associateWith { 0 }),
            diagram.edges to diagram.omitted,
        )
    }

    @Test
    fun `module diagrams project dependencies between module owned symbols`() {
        val fixture = DiagramGraph()
        val modules = fixture.structure().modules
        val dependency = Relationship(
            from = NodeRef.OfSymbol(
                SymbolIdentity.workspace(modules[0].id, null, SymbolDescriptor().term("input")).toSymbolId(),
            ),
            kind = RelationKind.DEPENDS_ON,
            subKind = "generator-input",
            resolution = Resolution.Resolved(
                NodeRef.OfSymbol(
                    SymbolIdentity.workspace(modules[1].id, null, SymbolDescriptor().term("spec")).toSymbolId(),
                ),
                Certainty.EXACT,
            ),
            evidenceClass = EvidenceClass.BUILD_MODEL,
            site = null,
            scope = Scope(),
        )
        val graph = object : GraphReader by fixture {
            override fun relationships(query: RelationshipQuery): List<Relationship> = listOf(dependency)
                .filter { query.kinds.isEmpty() || it.kind in query.kinds }
        }
        val diagram = (
            DiagramBuilder(graph).build(DiagramRequest(DiagramKind.MODULES)) as DiagramResult.Built
            ).diagram
        val labels = diagram.nodes.associate { it.id to it.label }
        assertEquals(listOf(":module0" to ":module1"), diagram.edges.map { labels[it.from] to labels[it.to] })
    }

    @Test
    fun `module diagrams match all three goldens`() {
        val diagram = (
            DiagramBuilder(DiagramGraph()).build(
                DiagramRequest(DiagramKind.MODULES),
            ) as DiagramResult.Built
            ).diagram
        val rendered = mapOf(
            "mmd" to MermaidEmitter.render(diagram),
            "dot" to DotEmitter.render(diagram),
            "json" to DiagramJson.render(diagram),
        )
        rendered.forEach { (extension, actual) ->
            val expected = requireNotNull(javaClass.getResource("/modules.$extension")).readText()
            assertEquals(expected, actual, extension)
        }
    }
}
