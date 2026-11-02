package io.github.krosstack.kscope.analyzer.android

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class BuildConfigSynthesizerTest : BuildConfigTestBase() {
    private val app = "sample.droid.app"
    private val lib = "sample.droid.lib"

    @Test
    fun `only modules that turn the feature on get a stub, in their main source set`() {
        val result = run()
        val stubs = result.sources.map { it.virtualPath }
        assertEquals(listOf("android-buildconfig/sample/droid/app/BuildConfig.java"), stubs)
        val source = result.source(app)
        assertEquals("java", source.language)
        assertEquals(DroidModel.sourceSetId(":app", "main"), source.sourceSet)
        assertEquals("android.buildconfig", source.generator)
    }

    @Test
    fun `an application has the application id and the version, and the custom fields follow by name`() {
        val standard = listOf("DEBUG", "APPLICATION_ID", "BUILD_TYPE", "VERSION_CODE", "VERSION_NAME")
        val declared = listOf("MAX_TASKS", "SYNC_ENDPOINT", "VERBOSE_LOGGING")
        assertEquals(standard + declared, run().fieldNames(app))
    }

    @Test
    fun `a library has the package name and no version`() {
        val result = run(libAttributes = libBuildConfig)
        assertEquals(listOf("DEBUG", "LIBRARY_PACKAGE_NAME", "BUILD_TYPE"), result.fieldNames(lib))
    }

    @Test
    fun `flavors add the flavor and, when the dimensions are known, one field per dimension`() {
        val dimensions = appBuildConfig + ("android.flavorDimensions" to "tier,store")
        val known = run(appAttributes = dimensions, flavors = listOf("demo", "full"))
        assertEquals(listOf("FLAVOR", "FLAVOR_tier", "FLAVOR_store"), known.flavorFields(app))
        assertEquals(listOf("FLAVOR"), run(flavors = listOf("demo", "full")).flavorFields(app))
        assertEquals(emptyList(), run().flavorFields(app))
    }

    @Test
    fun `the stubs match the golden text`() {
        val result = run(
            appAttributes = appBuildConfig + mapOf(
                "android.flavorDimensions" to "tier",
                "android.buildConfigField.IDS" to "String[]",
            ),
            flavors = listOf("demo"),
            libAttributes = libBuildConfig,
        )
        Golden.assertMatches("buildconfig-app.java", result.source(app).text)
        Golden.assertMatches("buildconfig-lib.java", result.source(lib).text)
    }

    @Test
    fun `fields get values that are valid for their type`() {
        val text = run(
            appAttributes = appBuildConfig + mapOf(
                "android.buildConfigField.RATIO" to "double",
                "android.buildConfigField.LIMIT" to "long",
                "android.buildConfigField.NAMES" to "java.util.List<String>",
            ),
        ).source(app).text
        assertTrue("public static final boolean DEBUG = false;" in text)
        assertTrue("public static final int MAX_TASKS = 0;" in text)
        assertTrue("public static final double RATIO = 0;" in text)
        assertTrue("public static final long LIMIT = 0;" in text)
        assertTrue("public static final String SYNC_ENDPOINT = \"\";" in text)
        assertTrue("public static final java.util.List<String> NAMES = null;" in text)
    }

    @Test
    fun `fields whose names or types Java cannot take are left out and counted, and standard names stay`() {
        val result = run(
            appAttributes = appBuildConfig + mapOf(
                "android.buildConfigField.new" to "int",
                "android.buildConfigField.BAD_TYPE" to "int;} class X {",
                "android.buildConfigField.DEBUG" to "String",
            ),
        )
        assertEquals(1, result.fieldNames(app).count { it == "DEBUG" })
        assertTrue("public static final boolean DEBUG = false;" in result.source(app).text)
        val skipped = result.facts.filterIsInstance<Fact.OfDiagnostic>()
            .single { it.value.code.value == "android.buildconfig.field-skipped" }
        assertTrue(skipped.value.message.startsWith(":app: 2 build config fields"))
    }

    @Test
    fun `symbols are generated java fields with signatures as the stub writes them`() {
        val field = run().symbols().single { it.qualifiedName == "$app.BuildConfig.SYNC_ENDPOINT" }
        assertEquals(":app@main:sample/droid/app/BuildConfig#SYNC_ENDPOINT.", Render.id(field.id))
        assertEquals("public static final String SYNC_ENDPOINT", field.signature)
        assertTrue(SymbolFlag.STATIC in field.flags)
        assertNull(field.declaration)
        assertEquals(EvidenceClass.CONVENTION, field.evidenceClass)
    }

    @Test
    fun `the build script generates the class and each of its fields`() {
        val result = run()
        val targets = result.generates().map { ((it.resolution as Resolution.Resolved).target as NodeRef.OfSymbol).id }
        assertEquals(result.symbols().map { it.id }.toSet(), targets.toSet())
        assertTrue(result.generates().all { Render.node(it.from) == "build.gradle.kts" })
        assertTrue(result.generates().all { it.evidenceClass == EvidenceClass.CONVENTION })
        assertTrue(result.generates().all { (it.resolution as Resolution.Resolved).certainty == Certainty.EXACT })
        val origin = result.symbols().first().origin as SymbolOrigin.Generated
        assertEquals(result.generates().first().from, origin.sourceOfTruth)
    }

    @Test
    fun `a module without a namespace says why it has no class, and one without a build script has none`() {
        val model = DroidModel.standard(appNamespace = null, appAttributes = appBuildConfig)
        val (batches, sources) = SampleTree("ui", model = model).synthesizeModules(synthesizer)
        assertEquals(emptyList(), sources)
        val codes = batches.facts().filterIsInstance<Fact.OfDiagnostic>().map { it.value.code.value }
        assertEquals(listOf("android.buildconfig.namespace-unknown"), codes)
        assertEquals(emptyList(), run(overrides = mapOf("app/build.gradle.kts" to "")).sources)
    }

    @Test
    fun `a module that leaves the feature off gets no class`() {
        val result = run(libAttributes = mapOf("android.buildFeatures.buildConfig" to "false"))
        val namespaces = result.sources.map { it.virtualPath.split('/').drop(1).dropLast(1).joinToString(".") }
        assertEquals(listOf(app), namespaces)
    }
}
