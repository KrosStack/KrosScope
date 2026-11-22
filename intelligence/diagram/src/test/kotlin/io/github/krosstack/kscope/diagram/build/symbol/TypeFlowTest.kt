package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.emit.DotEmitter
import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class TypeFlowTest {
    private val graph = SymbolGraph(typeFlowWorld())
    private val checkout = "app.Checkout"

    @Test
    fun `members roll up to their type and inheritance has its own edges`() {
        assertEquals(
            listOf(
                "app.Checkout -> app.Base [extends]",
                "app.Checkout -> app.Ledger [depends]",
                "app.Checkout -> app.Payable [implements]",
                "app.Checkout -> kotlin.unresolved-reference [calls]",
                "app.Checkout -> lib.Receipt [depends]",
                "app.Checkout.Step -> app.Checkout [depends]",
                "app.Report -> app.Checkout [depends]",
            ).sorted(),
            graph.diagram(checkout, depth = 1).edgeLines(),
        )
    }

    @Test
    fun `a call from a member to a member of the same type leaves no edge`() {
        val lines = graph.diagram(checkout, depth = 0).edgeLines()
        assertTrue(lines.none { it.startsWith("app.Checkout -> app.Checkout ") })
    }

    @Test
    fun `calls from members and from declarations nested in members count together`() {
        val diagram = graph.diagram(checkout, depth = 1)
        val edge = diagram.edgesBetween("app.Checkout", "app.Ledger").single()
        assertEquals("depends; compiler=2; exact=2", edge.label)
    }

    @Test
    fun `a nested type is its own node and a top level function has no type`() {
        val labels = graph.diagram(checkout, depth = 1).nodeLabels()
        assertTrue("app.Checkout.Step" in labels)
        assertTrue(labels.none { "main" in it })
    }

    @Test
    fun `depth walks both ways over type edges`() {
        assertEquals(
            setOf(
                "app.Checkout",
                "app.Base",
                "app.Payable",
                "app.Ledger",
                "lib.Receipt",
                "app.Report",
                "app.Checkout.Step",
            ),
            graph.diagram(checkout, depth = 1).nodeLabels(),
        )
        val deeper = graph.diagram(checkout, depth = 2)
        assertTrue("app.Audit" in deeper.nodeLabels())
        assertTrue("app.Ledger -> app.Audit [depends]" in deeper.edgeLines())
        assertEquals(deeper.nodeLabels(), graph.diagram(checkout, depth = 0).nodeLabels())
    }

    @Test
    fun `types are grouped by module and no edge carries a call position`() {
        val diagram = graph.diagram(checkout, depth = 1)
        assertEquals(setOf(":app", ":lib"), diagram.groups.map { it.label }.toSet())
        assertTrue(diagram.edges.all { it.position == null })
        assertEquals(listOf("app.Checkout"), diagram.nodes.filter { it.selected }.map { it.label })
    }

    @Test
    fun `inheritance edges are thick in mermaid and dot`() {
        val diagram = graph.diagram(checkout, depth = 1)
        val mermaid = MermaidEmitter.render(diagram)
        assertEquals(2, mermaid.lines().count { "==>" in it })
        assertTrue(mermaid.contains("==>|\"extends; compiler=1; exact=1\"|"))
        assertTrue(mermaid.contains(":::selected"))
        assertEquals(2, DotEmitter.render(diagram).lines().count { "penwidth=3" in it })
    }

    @Test
    fun `selecting a nested type reads only its own members`() {
        val diagram = graph.diagram("app.Checkout.Step", depth = 1)
        assertEquals(
            listOf(
                "app.Checkout -> kotlin.unresolved-reference [calls]",
                "app.Checkout.Step -> app.Checkout [depends]",
            ),
            diagram.edgeLines(),
        )
    }
}
