package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DrawnState
import io.github.krosstack.kscope.diagram.emit.DiagramJson
import io.github.krosstack.kscope.diagram.emit.DotEmitter
import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.RelationKind
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class MergedEdgeTest {
    private fun threeWays(): SymbolWorld {
        val world = SymbolWorld()
        val alpha = world.type("Alpha")
        val beta = world.type("Beta")
        val target = world.function("go", beta)
        world.call(world.function("run", alpha), target, 1)
        world.relate(world.property("use", alpha), RelationKind.REFERENCES, target)
        world.relate(world.function("make", alpha), RelationKind.TYPE_USES, beta)
        return world
    }

    private fun Diagram.only(from: String, to: String) = edgesBetween(from, to).single()

    @Test
    fun `calls references and type uses between two types make one edge`() {
        val diagram = SymbolGraph(threeWays()).diagram("app.Alpha")
        val edge = diagram.only("app.Alpha", "app.Beta")
        assertEquals(1, diagram.edges.size)
        assertEquals("depends; compiler=3; exact=3", edge.label)
        assertEquals(mapOf("calls" to 1, "references" to 1, "type-uses" to 1), edge.byKind)
        assertEquals(mapOf(DrawnState.EXACT to 3), edge.byState)
    }

    @Test
    fun `several relationships of one kind are counted on the edge`() {
        val world = threeWays()
        val run = world.symbols.values.first { it.name == "run" }.id
        val target = world.symbols.values.first { it.name == "go" }.id
        world.call(run, target, 2)
        val edge = SymbolGraph(world).diagram("app.Alpha").only("app.Alpha", "app.Beta")
        assertEquals(mapOf("calls" to 2, "references" to 1, "type-uses" to 1), edge.byKind)
    }

    @Test
    fun `the other direction stays an edge of its own`() {
        val world = threeWays()
        val back = world.function("back", world.symbols.values.first { it.name == "Beta" }.id)
        world.call(back, world.symbols.values.first { it.name == "run" }.id, 9)
        val diagram = SymbolGraph(world).diagram("app.Alpha")
        assertEquals(2, diagram.edges.size)
        assertEquals(mapOf("calls" to 1), diagram.only("app.Beta", "app.Alpha").byKind)
    }

    @Test
    fun `inheritance stays an edge beside the merged one`() {
        val world = threeWays()
        val alpha = world.symbols.values.first { it.name == "Alpha" }.id
        world.relate(alpha, RelationKind.EXTENDS, world.symbols.values.first { it.name == "Beta" }.id)
        val diagram = SymbolGraph(world).diagram("app.Alpha")
        val labels = diagram.edgesBetween("app.Alpha", "app.Beta").map { it.label.substringBefore(';') }
        assertEquals(listOf("depends", "extends"), labels.sorted())
        val inheritance = diagram.edgesBetween("app.Alpha", "app.Beta").single { it.label.startsWith("extends") }
        assertTrue(inheritance.byKind.isEmpty())
    }

    @Test
    fun `the weakest state of the kinds is the state of the edge`() {
        val world = threeWays()
        val run = world.symbols.values.first { it.name == "run" }.id
        val target = world.symbols.values.first { it.name == "go" }.id
        world.call(run, target, 3, Certainty.PROBABLE)
        val edge = SymbolGraph(world).diagram("app.Alpha").only("app.Alpha", "app.Beta")
        assertEquals(DrawnState.PROBABLE, edge.weakest)
        assertEquals(mapOf(DrawnState.EXACT to 3, DrawnState.PROBABLE to 1), edge.byState)
    }

    @Test
    fun `an unresolved relationship keeps its own kind and does not join the merged edge`() {
        val world = threeWays()
        world.unresolvedCall(world.symbols.values.first { it.name == "run" }.id, 5)
        val diagram = SymbolGraph(world).diagram("app.Alpha")
        val reason = diagram.edges.single { it.to in diagram.reasons.map { reason -> reason.id } }
        assertTrue(reason.label.startsWith("calls;"))
        assertTrue(reason.byKind.isEmpty())
        assertEquals(3, diagram.only("app.Alpha", "app.Beta").byState.values.sum())
    }

    @Test
    fun `the link budget draws one neighbour per edge`() {
        val world = SymbolWorld()
        val hub = world.type("Hub")
        val member = world.function("m", hub)
        repeat(30) {
            val neighbour = world.type("N${it.toString().padStart(2, '0')}")
            world.call(world.function("run", neighbour), member, it + 1)
            world.relate(world.property("p", neighbour), RelationKind.REFERENCES, member)
            world.relate(world.function("q", neighbour), RelationKind.TYPE_USES, hub)
        }
        val diagram = SymbolGraph(world).diagram("app.Hub", links = 20)
        assertEquals(20, diagram.edges.size)
        assertEquals(21, diagram.nodeLabels().size)
    }

    @Test
    fun `all three outputs show the total count and the kind counts are in the json only`() {
        val diagram = SymbolGraph(threeWays()).diagram("app.Alpha")
        val text = "depends; compiler=3; exact=3"
        val mermaid = MermaidEmitter.render(diagram)
        val dot = DotEmitter.render(diagram)
        val json = DiagramJson.render(diagram)
        assertTrue(mermaid.contains("-->|\"$text\"|"))
        assertTrue(dot.contains("label=\"$text\""))
        assertTrue(json.contains("\"label\": \"$text\""))
        assertTrue(mermaid.contains("depends: calls, references and type-uses between two types"))
        assertFalse(mermaid.contains("references=1"))
        assertFalse(dot.contains("type-uses=1"))
        assertTrue(json.contains("\"byKind\": {"))
        assertTrue(json.contains("\"type-uses\": 1"))
    }

    @Test
    fun `a call flow diagram keeps one edge per kind of call and shows no kind counts`() {
        val diagram = SymbolGraph(callFlowWorld()).diagram("app.Checkout.pay", depth = 2)
        assertFalse(DiagramJson.render(diagram).contains("byKind"))
        assertFalse(MermaidEmitter.render(diagram).contains("depends"))
        assertTrue(diagram.edges.all { it.label.startsWith("calls;") })
    }
}
