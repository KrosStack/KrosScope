package io.github.krosstack.kscope.analyzer.android.manifest

import io.github.krosstack.kscope.analyzer.android.DroidModel
import io.github.krosstack.kscope.analyzer.android.Render
import io.github.krosstack.kscope.analyzer.android.SampleTree
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.SymbolCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ManifestSymbolsTest : ManifestTestBase() {

    @Test
    fun `the application, each component and each permission is a component symbol of the kind of its tag`() {
        val symbols = symbolsOf()
        assertEquals(
            listOf(
                "android.manifest.permission com.example.ui.permission.SYNC",
                "android.manifest.application .UiApp",
                "android.manifest.activity .MainActivity",
                "android.manifest.activity SettingsActivity",
                "android.manifest.activity com.example.ui.detail.DetailActivity",
                "android.manifest.activity .Outer\$Inner",
                "android.manifest.activity-alias .QuickAdd",
                "android.manifest.service .SyncService",
                "android.manifest.receiver .BootReceiver",
                "android.manifest.provider .ExportProvider",
                "android.manifest.provider androidx.startup.InitializationProvider",
            ).sorted(),
            symbols.map { "${it.kind.id} ${it.name}" }.sorted(),
        )
        assertTrue(symbols.all { it.kind.category == SymbolCategory.COMPONENT })
    }

    @Test
    fun `a name is resolved against the namespace of the module in each of its three forms`() {
        val classes = symbolsOf().associate { it.name to it.attributes[CLASS_ATTRIBUTE] }
        assertEquals("sample.droid.app.MainActivity", classes[".MainActivity"])
        assertEquals("sample.droid.app.SettingsActivity", classes["SettingsActivity"])
        assertEquals("com.example.ui.detail.DetailActivity", classes["com.example.ui.detail.DetailActivity"])
        assertEquals("sample.droid.app.Outer.Inner", classes[".Outer\$Inner"])
    }

    @Test
    fun `the identity names the class a component is named for, in the source set of its manifest`() {
        val ids = symbolsOf().associate { it.name to Render.id(it.id) }
        assertEquals(":app@main:android/manifest/activity/`sample.droid.app.MainActivity`.", ids[".MainActivity"])
        assertEquals(
            ":app@main:android/manifest/permission/`com.example.ui.permission.SYNC`.",
            ids["com.example.ui.permission.SYNC"],
        )
        val diagnostics = symbolsOf(debug).single { it.kind.id == "android.manifest.activity" }
        assertEquals(
            ":app@debug:android/manifest/activity/`sample.droid.app.debug.DiagnosticsActivity`.",
            Render.id(diagnostics.id),
        )
    }

    @Test
    fun `exported and the actions of all intent filters become sorted attributes`() {
        val activity = symbolsOf().single { it.name == ".MainActivity" }
        assertEquals("true", activity.attributes[EXPORTED_ATTRIBUTE])
        assertEquals("android.intent.action.MAIN,android.intent.action.VIEW", activity.attributes[ACTIONS_ATTRIBUTE])
        val settings = symbolsOf().single { it.name == "SettingsActivity" }
        assertEquals("false", settings.attributes[EXPORTED_ATTRIBUTE])
        assertNull(settings.attributes[ACTIONS_ATTRIBUTE])
        assertNull(symbolsOf().single { it.name == ".Outer\$Inner" }.attributes[EXPORTED_ATTRIBUTE])
    }

    @Test
    fun `a permission is no class and a component outside the workspace namespaces keeps its written name`() {
        val symbols = symbolsOf()
        assertNull(symbols.single { it.kind.id == "android.manifest.permission" }.attributes[CLASS_ATTRIBUTE])
        val library = symbols.single { it.name == "androidx.startup.InitializationProvider" }
        val expected = ":app@main:android/manifest/provider/`androidx.startup.InitializationProvider`."
        assertEquals(expected, Render.id(library.id))
    }

    @Test
    fun `an application without a name is still a symbol, named after its tag`() {
        val application = symbolsOf(debug).single { it.kind.id == "android.manifest.application" }
        assertEquals("application", application.name)
        assertNull(application.attributes[CLASS_ATTRIBUTE])
        assertEquals(":app@debug:android/manifest/application/application.", Render.id(application.id))
    }

    @Test
    fun `the declaration is the element and the name location is the value of the name attribute`() {
        val activity = symbolsOf().single { it.name == ".MainActivity" }
        assertEquals("21:9-32:20", Render.at(activity.declaration))
        assertEquals("22:27-22:40", Render.at(activity.nameLocation))
    }

    @Test
    fun `without a namespace in the build model the legacy package attribute resolves the names`() {
        val legacy = SampleTree(
            "ui",
            model = DroidModel.standard(appNamespace = null),
            overrides = mapOf(
                main to "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"old.pkg\">" +
                    "<application><activity android:name=\".Home\" /></application></manifest>",
            ),
        )
        val home = symbolsOf(main, legacy).single { it.name == ".Home" }
        assertEquals("old.pkg.Home", home.attributes[CLASS_ATTRIBUTE])
    }

    @Test
    fun `a malformed manifest keeps what could be read and warns`() {
        val broken = SampleTree(
            "ui",
            overrides = mapOf(
                main to "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">" +
                    "<application android:name=\".A\"><activity android:name=\".B\"",
            ),
        )
        val warning = factsOf(main, broken).filterIsInstance<Fact.OfDiagnostic>().single().value
        assertEquals("android.manifest.xml-malformed", warning.code.value)
        assertEquals(DiagnosticSeverity.WARNING, warning.severity)
    }

    @Test
    fun `a manifest that no source set holds is noted and makes no symbols`() {
        val stray = SampleTree("ui", overrides = mapOf("stray/src/main/AndroidManifest.xml" to "<manifest />"))
        val facts = factsOf("stray/src/main/AndroidManifest.xml", stray)
        assertEquals(listOf("diagnostic INFO android.manifest.module-unknown"), Render.lines(facts))
    }
}
