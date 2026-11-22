package io.github.krosstack.kscope.diagram

import io.github.krosstack.kscope.diagram.build.symbol.SymbolGraph
import io.github.krosstack.kscope.diagram.build.symbol.callFlowWorld
import io.github.krosstack.kscope.diagram.build.symbol.diagram
import io.github.krosstack.kscope.diagram.emit.DiagramJson
import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class KindIsolationTest {
    private val symbolOnly = listOf(
        "selected",
        "position",
        "symbolId",
        "byKind",
        "notExpanded",
        "membersNotRead",
        "callsNotDrawn",
        "notInType",
        "notCallable",
        "withoutSymbol",
        "withinType",
        "beyondDepth",
        "stopped",
    )

    private val encodingDefaults = Json(KscopeJson) { prettyPrint = true }

    private fun existing(kind: DiagramKind): Diagram =
        (DiagramBuilder(DiagramGraph()).build(DiagramRequest(kind)) as DiagramResult.Built).diagram

    private fun Diagram.encodedWithDefaults(): String =
        encodingDefaults.encodeToString(Diagram.serializer(), this) + "\n"

    @Test
    fun `module and component json is byte identical when defaults and nulls are encoded`() {
        for (kind in listOf(DiagramKind.MODULES, DiagramKind.COMPONENTS)) {
            val golden = requireNotNull(javaClass.getResource("/${kind.name.lowercase()}.json")).readText()
            assertEquals(golden, existing(kind).encodedWithDefaults())
            assertEquals(golden, DiagramJson.render(existing(kind)))
        }
    }

    @Test
    fun `the new fields never appear in module and component json`() {
        for (kind in listOf(DiagramKind.MODULES, DiagramKind.COMPONENTS)) {
            val text = existing(kind).encodedWithDefaults()
            symbolOnly.forEach { assertFalse(text.contains("\"$it\""), it) }
        }
    }

    @Test
    fun `symbol json is the same with and without encoding defaults`() {
        val diagram = SymbolGraph(callFlowWorld()).diagram("app.Checkout.pay", depth = 2)
        assertEquals(DiagramJson.render(diagram), diagram.encodedWithDefaults())
        assertTrue(DiagramJson.render(diagram).contains("\"selected\": true"))
        assertTrue(DiagramJson.render(diagram).contains("\"position\": 1"))
    }
}
