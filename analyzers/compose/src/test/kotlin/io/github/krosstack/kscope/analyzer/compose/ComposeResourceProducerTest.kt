package io.github.krosstack.kscope.analyzer.compose

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeResourceProducerTest {
    private val root = "ui/shared/src/commonMain/composeResources"
    private val strings = """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <string name="priority_low">Low</string>
            <!-- <string name="commented_out">x</string> -->
            <plurals name="task_count">
                <item quantity="one">%d task</item>
                <item quantity="other">%d tasks</item>
            </plurals>
            <string-array name="priorities">
                <item>Low</item>
                <item>High</item>
            </string-array>
            <color name="ignored">#fff</color>
        </resources>
    """.trimIndent() + "\n"
    private val producer = ComposeResourceProducer()

    private fun tree(vararg files: Pair<String, String>) = ComposeTree(ModuleSpec(), files.toMap())

    @Test
    fun `strings plurals and string arrays of a values file are resource symbols`() {
        val tree = tree("$root/values/strings.xml" to strings)

        val symbols = tree.produce(producer, "$root/values/strings.xml").symbols()

        assertEquals(
            listOf(
                "compose.resource.string priority_low",
                "compose.resource.plurals task_count",
                "compose.resource.string-array priorities",
            ),
            symbols.map { "${it.kind.id} ${it.name}" },
        )
        symbols.forEach {
            assertEquals(SymbolCategory.RESOURCE, it.kind.category)
            assertEquals(EvidenceClass.STRUCTURAL, it.evidenceClass)
            assertEquals(Certainty.EXACT, it.certainty)
        }
    }

    @Test
    fun `a symbol is placed on the line and column of its element and its name`() {
        val tree = tree("$root/values/strings.xml" to strings)

        val low = tree.produce(producer, "$root/values/strings.xml").symbols().first()

        val declaration = checkNotNull(low.declaration).range
        val name = checkNotNull(low.nameLocation).range
        assertEquals(3, declaration.startLine)
        assertEquals(5, declaration.startColumn)
        assertEquals(3, declaration.endLine)
        assertEquals(3, name.startLine)
        assertEquals(19, name.startColumn)
        assertEquals(31, name.endColumn)
    }

    @Test
    fun `a multi-line element spans its lines`() {
        val tree = tree("$root/values/strings.xml" to strings)

        val plurals = tree.produce(producer, "$root/values/strings.xml").symbols()[1]

        val declaration = checkNotNull(plurals.declaration).range
        assertEquals(5, declaration.startLine)
        assertEquals(8, declaration.endLine)
    }

    @Test
    fun `the same resource in another configuration is the same symbol with another signature`() {
        val germanText = "<resources>\n    <string name=\"priority_low\">Niedrig</string>\n</resources>\n"
        val tree = tree(
            "$root/values/strings.xml" to strings,
            "$root/values-de/strings.xml" to germanText,
        )

        val default = tree.produce(producer, "$root/values/strings.xml")
        val german = tree.produce(producer, "$root/values-de/strings.xml")

        val defaultSymbol = default.symbols().first()
        val germanSymbol = german.symbols().single()
        assertEquals(defaultSymbol.id, germanSymbol.id)
        assertEquals("string priority_low", defaultSymbol.signature)
        assertEquals("string priority_low(values-de)", germanSymbol.signature)
        assertEquals("de", germanSymbol.attributes["compose.resource.qualifiers"])
        assertEquals(0, default.relationships().size)
        val definition = german.relationships().single()
        assertEquals(RelationKind.REFERENCES, definition.kind)
        assertEquals("definition", definition.subKind)
        assertEquals(Resolution.Resolved(NodeRef.OfSymbol(defaultSymbol.id), Certainty.EXACT), definition.resolution)
        assertEquals(EvidenceClass.STRUCTURAL, definition.evidenceClass)
    }

    @Test
    fun `a name defined twice in the default configuration is one symbol and a second definition site`() {
        val twice = "<resources>\n<string name=\"a\">1</string>\n<string name=\"a\">2</string>\n</resources>\n"
        val tree = tree("$root/values/strings.xml" to twice)

        val facts = tree.produce(producer, "$root/values/strings.xml")

        assertEquals(1, facts.symbols().size)
        assertEquals(3, facts.relationships().single().site?.range?.startLine)
    }

    @Test
    fun `drawables and fonts are named for their files, in any configuration`() {
        val tree = tree(
            "$root/drawable/ic_flag.xml" to "<vector/>\n",
            "$root/drawable-dark/ic_flag.png" to "binary",
            "$root/font/inter.ttf" to "binary",
        )

        val flag = tree.produce(producer, "$root/drawable/ic_flag.xml").symbols().single()
        val dark = tree.produce(producer, "$root/drawable-dark/ic_flag.png").symbols().single()
        val font = tree.produce(producer, "$root/font/inter.ttf").symbols().single()

        assertEquals("compose.resource.drawable", flag.kind.id)
        assertEquals("ic_flag", flag.name)
        assertEquals(flag.id, dark.id)
        assertEquals("compose.resource.font", font.kind.id)
        assertEquals("inter", font.name)
        assertEquals(1, checkNotNull(flag.nameLocation).range.startLine)
    }

    @Test
    fun `resources of different source sets are different symbols`() {
        val tree = tree(
            "$root/values/strings.xml" to strings,
            "ui/shared/src/desktopMain/composeResources/values/strings.xml" to strings,
        )

        val common = tree.produce(producer, "$root/values/strings.xml").symbols().first()
        val desktop = tree.produce(producer, "ui/shared/src/desktopMain/composeResources/values/strings.xml")
            .symbols().first()

        assertTrue(common.id != desktop.id)
        assertEquals(tree.sourceSetId("desktopMain"), desktop.scope.sourceSet)
    }

    @Test
    fun `a malformed file keeps what was read before the failure and says so`() {
        val broken = "<resources>\n<string name=\"a\">1</string>\n<string name=\"b\">2\n</resources>\n"
        val tree = tree("$root/values/strings.xml" to broken)

        val facts = tree.produce(producer, "$root/values/strings.xml")

        assertEquals(listOf("a"), facts.symbols().map { it.name })
        val warning = facts.filterIsInstance<Fact.OfDiagnostic>().single().value
        assertEquals("compose.resource.xml-malformed", warning.code.value)
    }
}
