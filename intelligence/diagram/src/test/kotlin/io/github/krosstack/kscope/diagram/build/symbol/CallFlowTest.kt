package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.DiagramEdge
import io.github.krosstack.kscope.diagram.DiagramKind
import io.github.krosstack.kscope.diagram.DrawnState
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class CallFlowTest {
    private val graph = SymbolGraph(callFlowWorld())
    private val pay = "app.Checkout.pay"

    @Test
    fun `a cycle through the selected function shows as a loop and every callable once`() {
        val diagram = graph.diagram(pay, depth = 0)
        val lines = diagram.edgeLines()
        assertTrue("fun pay() -> fun record() [calls #2]" in lines)
        assertTrue("fun record() -> fun pay() [calls #1]" in lines)
        assertEquals(DiagramKind.SYMBOL, diagram.kind)
        assertEquals(diagram.nodes.size, diagram.nodeLabels().size)
    }

    @Test
    fun `every callable that calls or is called transitively is drawn with every call between them`() {
        val diagram = graph.diagram(pay, depth = 0)
        assertEquals(
            setOf(
                "fun pay()",
                "fun validate()",
                "fun record()",
                "fun main()",
                "fun log()",
                "fun other()",
                "fun flush()",
                "fun entry()",
            ),
            diagram.nodeLabels(),
        )
        assertEquals(
            listOf(
                "fun entry() -> fun other() [calls #1]",
                "fun log() -> fun flush() [calls #1]",
                "fun main() -> fun pay() [calls #1]",
                "fun other() -> fun main() [calls #1]",
                "fun pay() -> fun record() [calls #2]",
                "fun pay() -> fun validate() [calls #1]",
                "fun pay() -> jni.native-side-not-analysed [calls #5]",
                "fun pay() -> kotlin.unresolved-reference [calls #4]",
                "fun record() -> fun pay() [calls #1]",
                "fun validate() -> fun log() [calls #1]",
            ),
            diagram.edgeLines(),
        )
    }

    @Test
    fun `callers of callees and callees of callers stay out`() {
        val diagram = graph.diagram(pay, depth = 0)
        assertTrue("fun audit()" !in diagram.nodeLabels())
    }

    @Test
    fun `depth counts edges from the selected callable in both directions and zero reaches the boundary`() {
        assertEquals(
            setOf("fun pay()", "fun validate()", "fun record()", "fun main()"),
            graph.diagram(pay, depth = 1).nodeLabels(),
        )
        assertEquals(
            setOf("fun pay()", "fun validate()", "fun record()", "fun main()", "fun log()", "fun other()"),
            graph.diagram(pay, depth = 2).nodeLabels(),
        )
        assertEquals(8, graph.diagram(pay, depth = 0).nodeLabels().size)
    }

    @Test
    fun `depth one keeps only the calls between the drawn callables`() {
        assertEquals(
            listOf(
                "fun main() -> fun pay() [calls #1]",
                "fun pay() -> fun record() [calls #2]",
                "fun pay() -> fun validate() [calls #1]",
                "fun pay() -> jni.native-side-not-analysed [calls #5]",
                "fun pay() -> kotlin.unresolved-reference [calls #4]",
                "fun record() -> fun pay() [calls #1]",
            ),
            graph.diagram(pay, depth = 1).edgeLines(),
        )
    }

    @Test
    fun `an unresolved and a not analysed call of an included function are drawn with their reasons`() {
        val diagram = graph.diagram(pay, depth = 1)
        assertEquals(
            listOf("jni.native-side-not-analysed", "kotlin.unresolved-reference"),
            diagram.reasons.map { it.reason },
        )
        val states = diagram.edges.filter { it.to.startsWith("r") }.associate { it.to to it.weakest }
        assertEquals(mapOf("r1" to DrawnState.NOT_ANALYSED, "r2" to DrawnState.UNRESOLVED), states)
    }

    @Test
    fun `call positions follow the source order and parallel calls keep the first`() {
        val diagram = graph.diagram(pay, depth = 1)
        val byTarget = diagram.edges.filter { it.from == "n1" }.associateBy { it.to }
        assertEquals(1, byTarget.getValue("n2").position)
        assertEquals(2, byTarget.getValue("n3").position)
        assertEquals(4, byTarget.getValue("r2").position)
        assertEquals(5, byTarget.getValue("r1").position)
        assertEquals(
            DiagramEdge(
                "n1",
                "n2",
                "calls; compiler=2; exact=2",
                DrawnState.EXACT,
                mapOf(DrawnState.EXACT to 2),
                mapOf("COMPILER" to 2),
                1,
            ),
            byTarget.getValue("n2"),
        )
    }

    @Test
    fun `groups are the declaring type or the file of a top level function`() {
        val diagram = graph.diagram(pay, depth = 0)
        assertEquals(setOf("app.Checkout", "app.Ledger", "src/main/Top.kt"), diagram.groups.map { it.label }.toSet())
    }

    @Test
    fun `only the chosen function is marked as selected`() {
        val diagram = graph.diagram(pay, depth = 0)
        assertEquals(listOf("fun pay()"), diagram.nodes.filter { it.selected }.map { it.label })
    }

    @Test
    fun `two symbols with the same label and group keep their own ids`() {
        val world = SymbolWorld()
        val owner = world.type("Checkout")
        val kind = SymbolKind("kotlin.function", SymbolCategory.CALLABLE)
        val declaration = Declaration("run", kind, owner, "app", "src/main/Checkout.kt")
        val first = world.enroll(SymbolId("first run"), declaration, "app.Checkout.run")
        val second = world.enroll(SymbolId("second run"), declaration, "app.Checkout.run")
        val main = world.function("main")
        world.call(main, first, 1)
        world.call(main, second, 2)
        val diagram = SymbolGraph(world).diagram("app.main", depth = 1)
        val nodes = diagram.nodes.associateBy { it.id }
        val targets = diagram.edges.associate { it.position to nodes.getValue(it.to) }
        val firstNode = targets.getValue(1)
        val secondNode = targets.getValue(2)
        assertEquals(firstNode.label, secondNode.label)
        assertEquals(firstNode.group, secondNode.group)
        assertEquals(first.value, firstNode.symbolId)
        assertEquals(second.value, secondNode.symbolId)
        assertNotEquals(firstNode.symbolId, secondNode.symbolId)
        assertEquals(main.value, diagram.nodes.single { it.selected }.symbolId)
        assertEquals(3, diagram.nodes.size)
    }
}
