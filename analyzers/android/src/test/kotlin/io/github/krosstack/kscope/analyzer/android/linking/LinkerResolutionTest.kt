package io.github.krosstack.kscope.analyzer.android.linking

import io.github.krosstack.kscope.analyzer.android.SampleTree
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import kotlin.test.Test
import kotlin.test.assertEquals

internal class LinkerResolutionTest : LinkerTestBase() {

    @Test
    fun `a reference to a resource of the same module resolves exactly with textual evidence`() {
        val answer = link().answer("string/app_name", "layout/activity_main.xml")
        assertEquals(":app@main:android/res/string/app_name.", answer.target())
        assertEquals(Certainty.EXACT, answer.certainty())
        assertEquals(EvidenceClass.TEXTUAL, answer.evidenceClass)
        assertEquals("REFERENCES", answer.kind.name)
    }

    @Test
    fun `the definition of the variant source set wins for references made in it and main wins for main`() {
        val linked = link()
        val debugDefinition = ":app@debug:android/res/string/app_name."
        val mainDefinition = ":app@main:android/res/string/app_name."
        assertEquals(debugDefinition, linked.answer("string/app_name", "debug/res/values/strings.xml").target())
        assertEquals(mainDefinition, linked.answer("string/app_name", "layout/activity_main.xml").target())
    }

    @Test
    fun `a reference from main to a resource only a variant defines resolves to that definition`() {
        val tree = SampleTree(
            "droid",
            overrides = mapOf(
                "app/src/main/res/values/extra.xml" to
                    "<resources><string name=\"x\">@string/debug_banner</string></resources>",
            ),
        )
        val answer = link(tree).answer("string/debug_banner", "values/extra.xml")
        assertEquals(":app@debug:android/res/string/debug_banner.", answer.target())
        assertEquals(Certainty.EXACT, answer.certainty())
    }

    @Test
    fun `the module wins over the modules it depends on`() {
        val answer = link().answer("string/shadowed", "layout/view_badge.xml")
        assertEquals(":app@main:android/res/string/shadowed.", answer.target())
        assertEquals(Certainty.EXACT, answer.certainty())
    }

    @Test
    fun `a resource of one dependency resolves exactly`() {
        val answer = link().answer("string/lib_title", "layout/view_badge.xml")
        assertEquals(":lib@main:android/res/string/lib_title.", answer.target())
        assertEquals(Certainty.EXACT, answer.certainty())
    }

    @Test
    fun `a resource that several dependencies provide resolves to the nearest one as probable`() {
        val answer = link().answer("string/in_two_libs", "layout-land/activity_main.xml")
        assertEquals(":lib@main:android/res/string/in_two_libs.", answer.target())
        assertEquals(Certainty.PROBABLE, answer.certainty())
        assertEquals(mapOf("android.resource.candidates" to "2"), answer.attributes)
    }
}
