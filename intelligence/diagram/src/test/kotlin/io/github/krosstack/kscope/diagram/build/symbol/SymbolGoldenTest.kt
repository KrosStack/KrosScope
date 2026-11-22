package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.emit.DiagramJson
import io.github.krosstack.kscope.diagram.emit.DotEmitter
import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class SymbolGoldenTest {
    private fun assertMatches(name: String, diagram: Diagram) {
        val rendered = mapOf(
            "mmd" to MermaidEmitter.render(diagram),
            "dot" to DotEmitter.render(diagram),
            "json" to DiagramJson.render(diagram),
        )
        rendered.forEach { (extension, actual) ->
            val expected = requireNotNull(javaClass.getResource("/$name.$extension")).readText()
            assertEquals(expected, actual, "$name.$extension")
        }
    }

    @Test
    fun `call flow diagrams match all three goldens`() {
        assertMatches("symbol-calls", SymbolGraph(callFlowWorld()).diagram("app.Checkout.pay", depth = 2))
    }

    @Test
    fun `type diagrams match all three goldens`() {
        assertMatches("symbol-types", SymbolGraph(typeFlowWorld()).diagram("app.Checkout", depth = 1))
    }

    @Test
    fun `cut call flow diagrams match all three goldens`() {
        assertMatches("symbol-cut", SymbolGraph(callFlowWorld()).diagram("app.Checkout.pay", depth = 0, links = 4))
    }
}
