package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.DiagramResult
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class SymbolOutcomesTest {
    private val graph = SymbolGraph(typeFlowWorld())

    @Test
    fun `a request without a node is not found`() {
        assertEquals(DiagramResult.NotFound(null), graph.result(null))
    }

    @Test
    fun `a name that matches no symbol is not found`() {
        assertEquals(DiagramResult.NotFound("app.Nothing"), graph.result("app.Nothing"))
    }

    @Test
    fun `a symbol that is neither a callable nor a type is not supported`() {
        val result = assertIs<DiagramResult.NotSupported>(graph.result("app.Checkout.total"))
        assertEquals("app.Checkout.total", result.symbol.qualifiedName)
    }

    @Test
    fun `a name shared by several symbols is ambiguous and lists them in canonical order`() {
        val world = SymbolWorld()
        val second = world.type("B")
        val first = world.type("A")
        world.function("run", second)
        world.function("run", first)
        for (order in 0..2) {
            val result = assertIs<DiagramResult.Ambiguous>(SymbolGraph(world, order).result("run"))
            assertEquals("run", result.node)
            assertEquals(listOf("app.A.run", "app.B.run"), result.candidates.map { it.qualifiedName })
        }
    }

    @Test
    fun `the node can be an id a qualified name or a simple name`() {
        val world = callFlowWorld()
        val reader = SymbolGraph(world)
        val id = world.symbols.values.single { it.qualifiedName == "app.Checkout.pay" }.id.value
        val byId = reader.diagram(id)
        assertEquals(byId, reader.diagram("app.Checkout.pay"))
        assertEquals(byId, reader.diagram("pay"))
    }

    @Test
    fun `a character budget below the floor still reports the minimum`() {
        val result = SymbolGraph(callFlowWorld()).result("app.Checkout.pay", characters = 10)
        assertIs<DiagramResult.BudgetTooSmall>(result)
    }
}
