package io.github.krosstack.kscope.analyzer.android.manifest

import io.github.krosstack.kscope.analyzer.android.DroidModel
import io.github.krosstack.kscope.analyzer.android.Golden
import io.github.krosstack.kscope.analyzer.android.Render
import io.github.krosstack.kscope.analyzer.android.facts
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.SymbolicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ManifestReferencesTest : ManifestTestBase() {
    private val app = ":app@main:android/manifest"

    private fun SymbolicReference.describe() = "${Render.node(from)} $key ${Render.at(site)}"

    private fun noteCodes(): List<String> = factsOf().filterIsInstance<Fact.OfDiagnostic>().map { it.value.code.value }

    @Test
    fun `a component is bound to its implementing class by a textual reference of kind binds`() {
        val references = referencesOf(main, "jvm.class")
        assertTrue(references.all { it.kind == RelationKind.BINDS && it.evidenceClass == EvidenceClass.TEXTUAL })
        assertEquals(
            listOf(
                "sample.droid.app.UiApp",
                "sample.droid.app.MainActivity",
                "sample.droid.app.SettingsActivity",
                "com.example.ui.detail.DetailActivity",
                "sample.droid.app.Outer.Inner",
                "sample.droid.app.MainActivity",
                "sample.droid.app.SyncService",
                "sample.droid.app.BootReceiver",
                "sample.droid.app.ExportProvider",
            ),
            references.map { it.key },
        )
    }

    @Test
    fun `an alias is bound to its target activity and not to the class its own name would be`() {
        val alias = referencesOf(main, "jvm.class").single { it.subKind == "target-activity" }
        assertEquals("sample.droid.app.MainActivity", alias.key)
        assertTrue("QuickAdd" in Render.node(alias.from))
        assertTrue(referencesOf(main, "jvm.class").none { it.key.endsWith("QuickAdd") })
    }

    @Test
    fun `the site of a class reference is the value of the attribute that names the class`() {
        val application = referencesOf(main, "jvm.class").first()
        assertEquals("12:23-12:29", Render.at(application.site))
    }

    @Test
    fun `classes of the platform and of libraries are counted in one note and not referenced`() {
        assertTrue(referencesOf(main, "jvm.class").none { it.key.startsWith("androidx.") })
        val note = factsOf().filterIsInstance<Fact.OfDiagnostic>()
            .single { it.value.code.value == "android.class.framework-classes" }
        assertEquals("$main: 1 class names of the platform or of libraries are not linked.", note.value.message)
    }

    @Test
    fun `placeholders in attributes are neither class names nor resources`() {
        val classes = referencesOf(main, "jvm.class")
        val keys = (classes + referencesOf(main, "android.resource")).map { it.key }
        assertTrue(keys.none { "applicationId" in it })
        assertEquals(listOf("android.class.framework-classes", "android.resource.external-references"), noteCodes())
    }

    @Test
    fun `attributes of the application, of components and of elements below them name resources`() {
        val resources = referencesOf(main, "android.resource")
        assertEquals(
            listOf(
                "$app/application/`sample.droid.app.UiApp`. mipmap/ic_launcher 13:23-13:42",
                "$app/application/`sample.droid.app.UiApp`. string/app_name 14:24-14:40",
                "$app/application/`sample.droid.app.UiApp`. style/Theme.Ui 15:24-15:39",
                "$app/application/`sample.droid.app.UiApp`. xml/backup_rules 19:31-19:48",
                "$app/activity-alias/`sample.droid.app.QuickAdd`. string/quick_label 43:28-43:47",
                "$app/permission/`com.example.ui.permission.SYNC`. string/sync_description 6:30-6:54",
            ).sorted(),
            resources.map { it.describe() }.sorted(),
        )
    }

    @Test
    fun `resources of the platform are counted and not referenced`() {
        val note = factsOf().filterIsInstance<Fact.OfDiagnostic>()
            .single { it.value.code.value == "android.resource.external-references" }
        assertTrue("1 references" in note.value.message)
        assertTrue(referencesOf(main, "android.resource").none { it.key.contains("Theme.Light") })
    }

    @Test
    fun `the label of a component in an overlay manifest is a reference from the component of that overlay`() {
        val resources = referencesOf(debug, "android.resource")
        assertEquals(1, resources.size)
        val reference = resources.single()
        assertEquals("string/diagnostics_title", reference.key)
        assertTrue(Render.node(reference.from).startsWith(":app@debug:android/manifest/activity/"))
        assertEquals(DroidModel.sourceSetId(":app", "debug"), reference.scope.sourceSet)
    }

    @Test
    fun `the facts of the main manifest match the golden file`() {
        val batches = tree.produce(ManifestProducer()) { it.ref.path.value == main }
        Golden.assertMatches("manifest-main.jsonl", Golden.facts(batches))
    }
}
