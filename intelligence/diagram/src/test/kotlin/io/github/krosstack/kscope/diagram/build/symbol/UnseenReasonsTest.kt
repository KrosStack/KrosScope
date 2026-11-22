package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.Omission
import io.github.krosstack.kscope.diagram.emit.DiagramJson
import io.github.krosstack.kscope.diagram.emit.DotEmitter
import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class UnseenReasonsTest {
    private val quiet = Omission(0, emptyMap())

    @Test
    fun `a type relationship whose other end is in no type is counted as not in a type`() {
        val (world, member) = hubWithMember()
        world.call(world.function("top"), member, 1)
        world.call(world.facadeFunction("helper", "UtilKt"), member, 2)
        world.call(member, world.function("other"), 3)
        val diagram = SymbolGraph(world).diagram("app.Hub")
        assertEquals(quiet.copy(notInType = 3), diagram.reasons())
        assertTrue(diagram.edges.isEmpty())
    }

    @Test
    fun `a call whose target is a class or a property or whose caller is a property is counted as not callable`() {
        val world = constructionWorld()
        val maker = world.function("make2")
        world.call(world.property("holder", world.type("Holder")), maker, 20)
        val diagram = SymbolGraph(world).diagram("app.Factory.build")
        assertEquals(quiet.copy(notCallable = 2), diagram.reasons())
        assertEquals(mapOf("kotlin.class" to 1, "kotlin.property" to 1), diagram.omitted.callsNotDrawn)
        assertEquals(quiet.copy(notCallable = 1), SymbolGraph(world).diagram("app.make2").reasons())
    }

    @Test
    fun `a call to a symbol the index lacks or to something that is no symbol is counted as without a symbol`() {
        val world = SymbolWorld()
        val source = world.function("s")
        world.callMissing(source, 1)
        world.callOutside(source)
        assertEquals(quiet.copy(withoutSymbol = 2), SymbolGraph(world).diagram("app.s").reasons())
    }

    @Test
    fun `a type relationship to something that is no symbol is counted as without a symbol`() {
        val (world, member) = hubWithMember()
        world.callOutside(member)
        assertEquals(quiet.copy(withoutSymbol = 1), SymbolGraph(world).diagram("app.Hub").reasons())
    }

    @Test
    fun `a relationship between two members of one type is counted once as inside the type`() {
        val world = SymbolWorld()
        val hub = world.type("Hub")
        world.call(world.function("a", hub), world.function("b", hub), 1)
        assertEquals(quiet.copy(withinType = 1), SymbolGraph(world).diagram("app.Hub").reasons())
    }

    @Test
    fun `a relationship read at the last level and leading beyond it is counted as beyond the depth`() {
        val world = chainWorld(3)
        assertEquals(quiet.copy(beyondDepth = 1), SymbolGraph(world).diagram("app.f0000", depth = 1).reasons())
        assertEquals(quiet, SymbolGraph(world).diagram("app.f0000", depth = 2).reasons())
        assertEquals(quiet, SymbolGraph(world).diagram("app.f0000", depth = 0).reasons())
    }

    @Test
    fun `a call of a caller that the walk follows only upstream is counted as beyond the depth at every depth`() {
        val world = SymbolWorld()
        val selected = world.function("f")
        val caller = world.function("g")
        world.call(caller, selected, 1)
        world.call(caller, world.function("h"), 2)
        for (depth in 0..2) {
            assertEquals(quiet.copy(beyondDepth = 1), SymbolGraph(world).diagram("app.f", depth = depth).reasons())
        }
    }

    @Test
    fun `relationships to neighbours that the node cap kept out are counted as stopped`() {
        val diagram = SymbolGraph(hubWorld(150)).diagram("app.Hub", depth = 1, links = 20)
        assertEquals(20, diagram.edges.size)
        assertEquals(quiet.copy(stopped = 130), diagram.reasons())
    }

    @Test
    fun `the outputs print the reasons that are not zero`() {
        val diagram = SymbolGraph(typeFlowWorld()).diagram("app.Checkout", depth = 1)
        assertEquals(quiet.copy(notInType = 1, withinType = 1, beyondDepth = 1), diagram.reasons())
        val text = "; 1 links with an end in no type: a top-level function, a file facade or a class the index lacks" +
            "; 1 links between two members of one type: a type has no edge to itself" +
            "; 1 links from a symbol the walk did not follow: the depth limit, or a caller followed upstream only"
        assertTrue(MermaidEmitter.render(diagram).contains("not-analysed=0$text\""))
        assertTrue(DotEmitter.render(diagram).contains("not-analysed=0$text\""))
        val json = DiagramJson.render(diagram)
        assertTrue(json.contains("\"notInType\": 1"))
        assertTrue(json.contains("\"withinType\": 1"))
        assertTrue(json.contains("\"beyondDepth\": 1"))
        assertFalse(json.contains("\"stopped\""))
        assertFalse(json.contains("\"notCallable\""))
    }

    @Test
    fun `a diagram without such relationships prints none of the reasons`() {
        val diagram = SymbolGraph(callFlowWorld()).diagram("app.Checkout.pay", depth = 0)
        assertEquals(quiet, diagram.reasons())
        val json = DiagramJson.render(diagram)
        listOf("notInType", "notCallable", "withoutSymbol", "withinType", "beyondDepth", "stopped")
            .forEach { assertFalse(json.contains("\"$it\""), it) }
        assertTrue(MermaidEmitter.render(diagram).contains("not-analysed=0\"]"))
        assertTrue(DotEmitter.render(diagram).contains("not-analysed=0\", shape"))
    }
}
