package io.github.krosstack.kscope.analyzer.compose

import io.github.krosstack.kscope.analyzer.compose.res.ComposeResSynthesizer
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComposeResSynthesizerTest {
    private val root = "ui/shared/src/commonMain/composeResources"
    private val strings = "<resources>\n<string name=\"priority_low\">Low</string>\n" +
        "<plurals name=\"tasks\"><item quantity=\"one\">1</item></plurals>\n" +
        "<string-array name=\"levels\"><item>a</item></string-array>\n</resources>\n"
    private val synthesizer = ComposeResSynthesizer()
    private val explicit = mapOf(
        "compose.resources.packageOfResClass" to "sample.ui.resources",
        "compose.resources.publicResClass" to "true",
    )

    private fun tree(spec: ModuleSpec = ModuleSpec(attributes = explicit), vararg extra: Pair<String, String>) =
        ComposeTree(
            spec,
            mapOf(
                "$root/values/strings.xml" to strings,
                "$root/drawable/ic_flag.xml" to "<vector/>\n",
                "$root/font/inter.ttf" to "binary",
            ) + extra,
        )

    private fun idOf(set: String, build: SymbolDescriptor.() -> SymbolDescriptor): SymbolId {
        val pkg = SymbolDescriptor().namespace("sample").namespace("ui").namespace("resources")
        return SymbolIdentity.workspace(ModuleSpec().moduleId, set, pkg.build()).toSymbolId()
    }

    @Test
    fun `the stub declares Res with an object for each kind and an extension property for each resource`() {
        val synthesis = tree().synthesize(synthesizer)

        val source = synthesis.sources.single()

        assertEquals("kotlin", source.language)
        assertEquals("compose-res/sample/ui/resources/Res.kt", source.virtualPath)
        assertEquals(tree().sourceSetId("commonMain"), source.sourceSet)
        assertEquals("compose.res", source.generator)
        Golden.assertMatches("res-explicit.kt.txt", source.text)
    }

    @Test
    fun `the symbols have the ids that the analyzer derives from the stub syntax`() {
        val symbols = tree().synthesize(synthesizer).facts.symbols().associateBy { it.id }

        assertNotNull(symbols[idOf("commonMain") { type("Res") }])
        assertNotNull(symbols[idOf("commonMain") { type("Res").type("string") }])
        assertNotNull(symbols[idOf("commonMain") { type("Res").type("array") }])
        assertNotNull(symbols[idOf("commonMain") { type("Res").method("readBytes", "String") }])
        assertNotNull(symbols[idOf("commonMain") { extensionProperty("priority_low", "string") }])
        assertNotNull(symbols[idOf("commonMain") { extensionProperty("tasks", "plurals") }])
        assertNotNull(symbols[idOf("commonMain") { extensionProperty("levels", "array") }])
        assertNotNull(symbols[idOf("commonMain") { extensionProperty("ic_flag", "drawable") }])
        assertNotNull(symbols[idOf("commonMain") { extensionProperty("inter", "font") }])
        assertEquals(13, symbols.size)
    }

    @Test
    fun `generated symbols name the module or the resource as their source of truth`() {
        val symbols = tree().synthesize(synthesizer).facts.symbols().associateBy { it.qualifiedName }

        val res = checkNotNull(symbols["sample.ui.resources.Res"])
        val low = checkNotNull(symbols["sample.ui.resources.priority_low"])
        assertEquals(SymbolOrigin.Generated("compose.res", NodeRef.OfModule(ModuleSpec().moduleId)), res.origin)
        val origin = low.origin as SymbolOrigin.Generated
        val source = (origin.sourceOfTruth as NodeRef.OfSymbol).id
        assertEquals("compose.resource.string", assertNotNull(tree().produceSymbol(source)).kind.id)
        assertEquals(Visibility.PUBLIC, low.visibility)
        assertEquals(EvidenceClass.CONVENTION, low.evidenceClass)
        assertEquals(Certainty.EXACT, low.certainty)
        assertEquals(setOf(SymbolFlag.SUSPEND), checkNotNull(symbols["sample.ui.resources.Res.readBytes"]).flags)
        assertTrue(symbols.values.all { it.declaration == null && it.nameLocation == null })
    }

    private fun ComposeTree.produceSymbol(id: SymbolId): Symbol? =
        listOf("$root/values/strings.xml", "$root/drawable/ic_flag.xml", "$root/font/inter.ttf")
            .flatMap { produce(ComposeResourceProducer(), it).symbols() }.firstOrNull { it.id == id }

    @Test
    fun `each resource generates its accessor by convention`() {
        val tree = tree()
        val generates = tree.synthesize(synthesizer).facts.relationships()

        assertEquals(5, generates.size)
        generates.forEach {
            assertEquals(RelationKind.GENERATES, it.kind)
            assertEquals(EvidenceClass.CONVENTION, it.evidenceClass)
            assertEquals(Certainty.EXACT, (it.resolution as Resolution.Resolved).certainty)
        }
        val accessor = idOf("commonMain") { extensionProperty("priority_low", "string") }
        val source = generates.single { (it.resolution as Resolution.Resolved).target == NodeRef.OfSymbol(accessor) }
        val resource = (source.from as NodeRef.OfSymbol).id
        assertEquals("compose.resource.string", tree.produceSymbol(resource)?.kind?.id)
    }

    @Test
    fun `a resource in several configurations makes one accessor that every definition generates`() {
        val german = "<resources>\n<string name=\"priority_low\">Niedrig</string>\n</resources>\n"
        val desktop = "ui/shared/src/desktopMain/composeResources/values/strings.xml"
        val tree = tree(ModuleSpec(attributes = explicit), "$root/values-de/strings.xml" to german, desktop to strings)

        val facts = tree.synthesize(synthesizer).facts

        val accessor = NodeRef.OfSymbol(idOf("commonMain") { extensionProperty("priority_low", "string") })
        val sources = facts.relationships().filter { (it.resolution as Resolution.Resolved).target == accessor }
        assertEquals(2, sources.size, "common and desktop each have the resource; the two configurations share one")
        assertEquals(1, facts.symbols().count { it.qualifiedName == "sample.ui.resources.priority_low" })
    }

    @Test
    fun `without the package of the build the package is derived and everything is a heuristic`() {
        val spec = ModuleSpec(attributes = mapOf("compose.resources.group" to "tasktide.ui"))
        val tree = tree(spec)

        val synthesis = tree.synthesize(synthesizer)

        val path = "compose-res/tasktide/ui/shared/generated/resources/Res.kt"
        assertEquals(path, synthesis.sources.single().virtualPath)
        val res = synthesis.facts.symbols().first { it.qualifiedName == "tasktide.ui.shared.generated.resources.Res" }
        assertEquals(Certainty.HEURISTIC, res.certainty)
        assertEquals(Visibility.INTERNAL, res.visibility)
        val header = "package tasktide.ui.shared.generated.resources\n\ninternal object Res {"
        assertTrue(synthesis.sources.single().text.startsWith(header))
        synthesis.facts.relationships().forEach {
            assertEquals(Certainty.HEURISTIC, (it.resolution as Resolution.Resolved).certainty)
        }
    }

    @Test
    fun `the facts match the golden file`() {
        Golden.assertMatches("res-explicit-facts.jsonl", Golden.facts(tree().synthesize(synthesizer).facts))
    }
}
