package io.github.krosstack.kscope.analyzer.android.linking

import io.github.krosstack.kscope.analyzer.android.DroidModel
import io.github.krosstack.kscope.analyzer.android.ResourceProducer
import io.github.krosstack.kscope.analyzer.android.SampleTree
import io.github.krosstack.kscope.analyzer.android.facts
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.answerKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class LinkerFailuresTest : LinkerTestBase() {

    @Test
    fun `every symbolic reference gets exactly one relationship`() {
        val linked = link()
        assertTrue(linked.references.size > 40)
        assertEquals(
            linked.references.map { it.answerKey() }.groupingBy { it }.eachCount(),
            linked.relationships.map { it.answerKey() }.groupingBy { it }.eachCount(),
        )
    }

    @Test
    fun `without dependencies a resource of another module is not found`() {
        val tree = SampleTree("droid", DroidModel.standard(withDependencies = false))
        val answer = link(tree).answer("string/lib_title", "layout/view_badge.xml")
        val resolution = assertIs<Resolution.Unresolved>(answer.resolution)
        assertEquals("android.resource.not-found", resolution.reason.value)
        assertEquals("string/lib_title", resolution.hint)
    }

    @Test
    fun `a resource that nobody defines is unresolved with the reason not found`() {
        val linked = link()
        listOf(
            "string/missing_label" to "layout/activity_main.xml",
            "style/Theme.AppCompat.Light" to "values/styles.xml",
            "attr/textAppearanceHeadline6" to "layout/activity_main.xml",
        ).forEach { (key, file) ->
            val resolution = assertIs<Resolution.Unresolved>(linked.answer(key, file).resolution, key)
            assertEquals("android.resource.not-found", resolution.reason.value)
        }
    }

    @Test
    fun `ids styles layouts and attributes are looked up like any other resource`() {
        val linked = link()
        assertEquals(":app@main:android/res/id/title.", linked.answer("id/title", "layout/activity_main.xml").target())
        assertEquals(
            ":app@main:android/res/layout/view_badge.",
            linked.answer("layout/view_badge", "layout/activity_main.xml").target(),
        )
        assertEquals(
            ":app@main:android/res/style/`Widget.Title`.",
            linked.answer("style/Widget.Title", "layout/activity_main.xml").target(),
        )
        assertEquals(":app@main:android/res/attr/accent.", linked.answer("attr/accent", "values/attrs.xml").target())
        val destination = linked.answer("id/details", "navigation/nav_main.xml")
        assertEquals(":app@main:android/res/id/details.", destination.target())
    }

    @Test
    fun `a reference without a source set or with a malformed key is not analysed`() {
        val tree = SampleTree("droid")
        val template = tree.produce(
            ResourceProducer(),
        ).facts().filterIsInstance<Fact.OfSymbolicReference>().first().value
        val noScope = template.copy(scope = Scope(), key = "string/app_name")
        val malformed = template.copy(key = "nokey")
        val linked = link(tree, listOf(Fact.OfSymbolicReference(noScope), Fact.OfSymbolicReference(malformed)))
        val reasons = linked.relationships.map { it.resolution }.filterIsInstance<Resolution.NotAnalysed>()
            .map { it.reason.value }.sorted()
        assertEquals(listOf("android.resource.malformed-key", "android.resource.scope-unknown"), reasons)
    }

    @Test
    fun `the linker claims the resource namespace and works on the whole workspace`() {
        assertEquals(setOf("android.resource"), linker.namespaces)
        assertEquals(OwnershipKind.WORKSPACE, linker.descriptor.granularity)
    }

    @Test
    fun `linking is deterministic`() {
        val first = link().relationships
        val second = link().relationships
        assertEquals(first, second)
    }
}
