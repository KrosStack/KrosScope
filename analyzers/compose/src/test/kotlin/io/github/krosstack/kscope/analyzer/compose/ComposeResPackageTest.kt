package io.github.krosstack.kscope.analyzer.compose

import io.github.krosstack.kscope.analyzer.compose.res.ComposeResSynthesizer
import io.github.krosstack.kscope.model.Fact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeResPackageTest {
    private val root = "ui/shared/src/commonMain/composeResources"
    private val strings = "<resources>\n<string name=\"priority_low\">Low</string>\n</resources>\n"
    private val synthesizer = ComposeResSynthesizer()
    private val explicit = mapOf("compose.resources.packageOfResClass" to "sample.ui.resources")

    private fun tree(spec: ModuleSpec = ModuleSpec(attributes = explicit), vararg extra: Pair<String, String>) =
        ComposeTree(spec, mapOf("$root/values/strings.xml" to strings) + extra)

    @Test
    fun `the derived package follows the plugin for the group and the module name`() {
        fun packageOf(path: String, group: String?): String? {
            val attributes = group?.let { mapOf("compose.resources.group" to it) }.orEmpty()
            val synthesis = tree(ModuleSpec(path = path, attributes = attributes)).synthesize(synthesizer)
            return synthesis.sources.singleOrNull()?.text?.lineSequence()?.first()?.removePrefix("package ")
        }

        assertEquals("tasktide.desktop.generated.resources", packageOf(":desktop", "tasktide"))
        assertEquals("my_app.generated.resources", packageOf(":my-app", null))
        assertEquals("_1password.generated.resources", packageOf(":1Password", null))
        assertEquals("com.acme_corp.sample_lib.generated.resources", packageOf(":Sample-Lib", "com.Acme-Corp"))
    }

    @Test
    fun `a package that is no Kotlin package gets no stub and a note`() {
        val spec = ModuleSpec(attributes = mapOf("compose.resources.packageOfResClass" to "not a package"))

        val synthesis = tree(spec).synthesize(synthesizer)

        assertEquals(emptyList(), synthesis.sources)
        val note = synthesis.facts.filterIsInstance<Fact.OfDiagnostic>().single().value
        assertEquals("compose.res.package-unusable", note.code.value)
    }

    @Test
    fun `a module that does not apply the plugin gets no stub`() {
        val synthesis = tree(ModuleSpec(plugins = listOf("org.jetbrains.kotlin.multiplatform"))).synthesize(synthesizer)

        assertEquals(emptyList(), synthesis.sources)
        assertEquals(emptyList(), synthesis.facts)
    }

    @Test
    fun `names that are no Kotlin names have no accessor and a keyword is quoted`() {
        val odd = "<resources>\n<string name=\"a.b\">x</string>\n<string name=\"fun\">y</string>\n</resources>\n"
        val tree = tree(ModuleSpec(attributes = explicit), "$root/values-x/odd.xml" to odd)

        val synthesis = tree.synthesize(synthesizer)

        val names = synthesis.facts.symbols().map { it.name }
        assertTrue("a.b" !in names)
        assertTrue("fun" in names)
        val accessor = "internal val Res.string.`fun`: org.jetbrains.compose.resources.StringResource get() = TODO()"
        assertTrue(accessor in synthesis.sources.single().text)
        val note = synthesis.facts.filterIsInstance<Fact.OfDiagnostic>().single().value
        assertEquals("compose.res.name-skipped", note.code.value)
    }
}
