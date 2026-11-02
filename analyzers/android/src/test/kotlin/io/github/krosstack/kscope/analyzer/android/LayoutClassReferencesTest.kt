package io.github.krosstack.kscope.analyzer.android

import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.SymbolicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class LayoutClassReferencesTest {
    private val tree = SampleTree("ui")
    private val layout = "app/src/main/res/layout/activity_main.xml"
    private val navigation = "app/src/main/res/navigation/nav_main.xml"

    private fun classReferencesOf(path: String, from: SampleTree = tree): List<SymbolicReference> =
        from.produce(ResourceProducer()) { it.ref.path.value == path }.facts()
            .filterIsInstance<Fact.OfSymbolicReference>().map { it.value }.filter { it.namespace == "jvm.class" }

    private fun describe(references: List<SymbolicReference>) =
        references.map { "${it.subKind} ${it.key} ${Render.at(it.site)}" }

    @Test
    fun `a layout names the classes of its custom views, view elements, fragments, context and layout manager`() {
        assertEquals(
            listOf(
                "tools-context sample.droid.app.MainActivity 8:20-8:33",
                "custom-view com.example.ui.widget.PriorityView 30:6-30:40",
                "view-class com.example.ui.widget.PillView 37:16-37:46",
                "fragment-class com.example.ui.HomeFragment 43:23-43:50",
                "fragment-class com.example.ui.HostFragment 49:23-49:50",
                "layout-manager com.example.ui.widget.GridManager 57:28-57:61",
            ),
            describe(classReferencesOf(layout)),
        )
    }

    @Test
    fun `the reference leaves a layout, is textual and has a sub-kind for the way the class is named`() {
        val references = classReferencesOf(layout)
        assertTrue(references.all { it.kind == RelationKind.REFERENCES && it.evidenceClass == EvidenceClass.TEXTUAL })
        assertTrue(references.all { Render.node(it.from) == ":app@main:android/res/layout/activity_main." })
    }

    @Test
    fun `a layout manager written relative to the application is not a reference and platform classes are counted`() {
        val keys = classReferencesOf(layout).map { it.key }
        assertTrue(keys.none { it.endsWith("RelativeManager") })
        val note = tree.produce(ResourceProducer()) { it.ref.path.value == layout }.facts()
            .filterIsInstance<Fact.OfDiagnostic>().single { it.value.code.value == "android.class.framework-classes" }
        assertEquals("$layout: 3 class names of the platform or of libraries are not linked.", note.value.message)
    }

    @Test
    fun `a navigation graph names the classes of its destinations`() {
        assertEquals(
            listOf(
                "destination-class com.example.ui.HomeFragment 9:23-9:50",
                "destination-class sample.droid.app.MainActivity 14:23-14:36",
                "destination-class com.example.ui.ConfirmDialog 18:23-18:51",
            ),
            describe(classReferencesOf(navigation)),
        )
        val owner = Render.node(classReferencesOf(navigation).first().from)
        assertEquals(":app@main:android/res/navigation/nav_main.", owner)
    }

    @Test
    fun `the class names of every configuration of a layout are references of the same layout`() {
        val land = classReferencesOf("app/src/main/res/layout-land/activity_main.xml")
        assertEquals(emptyList(), land)
        val withClass = SampleTree(
            "ui",
            overrides = mapOf(
                "app/src/main/res/layout-land/activity_main.xml" to
                    "<com.example.ui.widget.PriorityView " +
                    "xmlns:android=\"http://schemas.android.com/apk/res/android\" />",
            ),
        )
        val references = classReferencesOf("app/src/main/res/layout-land/activity_main.xml", withClass)
        assertEquals(":app@main:android/res/layout/activity_main.", Render.node(references.single().from))
    }

    @Test
    fun `values files and layouts without classes make no class references`() {
        assertEquals(emptyList(), classReferencesOf("app/src/main/res/values/strings.xml"))
        assertEquals(emptyList(), classReferencesOf("app/src/main/res/layout/view_badge.xml"))
    }
}
