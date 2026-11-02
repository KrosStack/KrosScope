package io.github.krosstack.kscope.analyzer.android.viewbinding

import io.github.krosstack.kscope.analyzer.android.DroidModel
import io.github.krosstack.kscope.analyzer.android.Golden
import io.github.krosstack.kscope.analyzer.android.SampleTree
import io.github.krosstack.kscope.analyzer.android.facts
import io.github.krosstack.kscope.model.Fact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ViewBindingStubTest : ViewBindingTestBase() {

    @Test
    fun `a module that turns view binding on gets one stub per layout in its main source set`() {
        val result = run()
        assertEquals(
            listOf("ActivityMainBinding", "DebugBannerBinding", "ViewBadgeBinding", "ViewRowsBinding"),
            result.sources.map { it.virtualPath.substringAfterLast('/').removeSuffix(".java") },
        )
        val stub = result.source("ActivityMainBinding")
        assertEquals("java", stub.language)
        assertEquals(DroidModel.sourceSetId(":app", "main"), stub.sourceSet)
        assertEquals("android.viewbinding", stub.generator)
        assertEquals("android-viewbinding/sample/droid/app/databinding/ActivityMainBinding.java", stub.virtualPath)
    }

    @Test
    fun `modules that do not turn view binding on get nothing`() {
        val result = run(SampleTree("ui"))
        assertEquals(emptyList(), result.sources)
        assertEquals(emptyList(), result.facts)
    }

    @Test
    fun `the stubs match the golden text`() {
        val result = run()
        Golden.assertMatches("viewbinding-activity-main.java", result.source("ActivityMainBinding").text)
        Golden.assertMatches("viewbinding-view-rows.java", result.source("ViewRowsBinding").text)
        Golden.assertMatches("viewbinding-view-badge.java", result.source("ViewBadgeBinding").text)
    }

    @Test
    fun `every configuration of a layout makes one class, with the ids of all of them`() {
        val text = run().source("ActivityMainBinding").text
        assertTrue("android.widget.TextView landOnly" in text)
        assertTrue("android.view.View title" in text, "A TextView in one configuration and a Button in another")
        assertTrue("public android.view.View getRoot()" in text, "A LinearLayout and a FrameLayout as root")
    }

    @Test
    fun `a layout in a variant source set joins the classes of the module`() {
        val banner = run().source("DebugBannerBinding")
        assertEquals(DroidModel.sourceSetId(":app", "main"), banner.sourceSet)
        assertTrue("android.widget.TextView bannerText" in banner.text)
    }

    @Test
    fun `the type of a field is the class of its tag, with the platform packages for tags without a package`() {
        val text = run().source("ActivityMainBinding").text
        listOf(
            "com.example.ui.widget.PriorityView priority",
            "com.example.ui.widget.PillView pill",
            "android.view.View home",
            "androidx.fragment.app.FragmentContainerView host",
            "androidx.recyclerview.widget.RecyclerView entries",
            "android.webkit.WebView web",
            "android.view.View divider",
            "android.view.ViewStub stub",
        ).forEach { assertTrue("public final $it = null;" in text, it) }
    }

    @Test
    fun `an include with an id has the binding of the included layout and without an id no field`() {
        val text = run().source("ActivityMainBinding").text
        assertTrue("public final sample.droid.app.databinding.ViewBadgeBinding badge = null;" in text)
        assertTrue("public final android.view.View platformBar = null;" in text, "A layout that is not in the module")
        assertEquals(1, Regex("ViewBadgeBinding ").findAll(text).count())
    }

    @Test
    fun `ids become camel case fields and those that Java cannot take or that repeat another are left out`() {
        val text = run().source("ActivityMainBinding").text
        assertTrue(" itemCount2 = null;" in text)
        assertEquals(1, Regex("itemCount2").findAll(text).count(), "Two ids make the same name; the first one wins")
        assertTrue(" class = null;" !in text)
        val note = run().facts.filterIsInstance<Fact.OfDiagnostic>().map { it.value.code.value }
        assertTrue("android.viewbinding.field-skipped" in note)
    }

    @Test
    fun `layouts that ignore view binding or are data binding layouts have no class and are counted`() {
        val result = run()
        assertTrue(result.sources.none { "Hidden" in it.virtualPath || "ViewData" in it.virtualPath })
        val note = result.facts.filterIsInstance<Fact.OfDiagnostic>().single {
            it.value.code.value == "android.viewbinding.layout-skipped"
        }
        assertTrue(note.value.message.startsWith(":app: 2 layouts"), note.value.message)
    }

    @Test
    fun `a module with view binding but without a namespace makes nothing and says why`() {
        val model = DroidModel.standard(appNamespace = null, appAttributes = mapOf(VIEW_BINDING_FEATURE_ON to "true"))
        val result = run(SampleTree("ui", model = model))
        assertEquals(emptyList(), result.sources)
        assertEquals(
            listOf("android.viewbinding.namespace-unknown"),
            result.facts.filterIsInstance<Fact.OfDiagnostic>().map { it.value.code.value },
        )
    }

    @Test
    fun `the view binding type of a view replaces its class before the configurations are merged`() {
        val landscape = "<EditText xmlns:android=\"http://schemas.android.com/apk/res/android\"" +
            " xmlns:tools=\"http://schemas.android.com/tools\" android:id=\"@+id/badge_text\"" +
            " tools:viewBindingType=\"android.widget.TextView\"/>"
        val tree = treeWithBinding(mapOf("app/src/main/res/layout-land/view_badge.xml" to landscape))
        assertTrue("android.widget.TextView badgeText" in run(tree).source("ViewBadgeBinding").text)
    }
}
