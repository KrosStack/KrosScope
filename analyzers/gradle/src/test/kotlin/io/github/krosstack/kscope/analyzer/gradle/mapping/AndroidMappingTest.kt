package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.analyzer.gradle.ModelDocuments
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.SourceSetRole
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidMappingTest {
    private val sample = SampleModel.load("android-app-flavors.json")
    private val app = sample.module(":app")

    private fun variantsOf(sourceSetName: String) =
        sample.sourceSets(":app").getValue(sourceSetName).variants.map { it.value.substringAfter("#variant/") }

    @Test
    fun `an application with built-in kotlin has the application and kotlin android traits`() {
        assertEquals(setOf(ModuleTrait.ANDROID_APPLICATION, ModuleTrait.KOTLIN_ANDROID), app.traits)
        assertEquals("sample.tasktide.app", app.androidNamespace)
        assertEquals(listOf("com.android.application", "org.jetbrains.kotlin.plugin.compose"), app.plugins)
    }

    @Test
    fun `variants are the flavor and build type combinations`() {
        val variants = sample.model.variants.filter { it.module == app.id }
        assertEquals(listOf("demoDebug", "demoRelease", "fullDebug", "fullRelease"), variants.map { it.name })
        val demoDebug = variants.first()
        assertEquals("debug", demoDebug.buildType)
        assertEquals(listOf("demo"), demoDebug.flavors)
        assertEquals("tasktide//:app#variant/demoDebug", demoDebug.id.value)
    }

    @Test
    fun `an android module has the single android target`() {
        val targets = sample.model.targets.filter { it.module == app.id }
        assertEquals(listOf("android" to Platform.ANDROID), targets.map { it.name to it.platform })
    }

    @Test
    fun `source sets exist when they have a directory and belong to the variants they are assembled into`() {
        assertEquals(
            listOf("androidTest", "debug", "demo", "full", "main", "test"),
            sample.sourceSets(":app").keys.toList(),
        )
        assertEquals(listOf("demoDebug", "demoRelease", "fullDebug", "fullRelease"), variantsOf("main"))
        assertEquals(listOf("demoDebug", "demoRelease"), variantsOf("demo"))
        assertEquals(listOf("demoDebug", "fullDebug"), variantsOf("debug"))
    }

    @Test
    fun `test source sets belong to the variants that have the test component`() {
        assertEquals(listOf("demoDebug", "fullDebug"), variantsOf("test"))
        assertEquals(listOf("demoDebug", "fullDebug"), variantsOf("androidTest"))
    }

    @Test
    fun `roles follow the test kind of the source set`() {
        val sourceSets = sample.sourceSets(":app")
        assertEquals(SourceSetRole.MAIN, sourceSets.getValue("debug").role)
        assertEquals(SourceSetRole.UNIT_TEST, sourceSets.getValue("test").role)
        assertEquals(SourceSetRole.INSTRUMENTED_TEST, sourceSets.getValue("androidTest").role)
    }

    @Test
    fun `roots carry their kind and the manifest is a root of its own`() {
        val roots = sample.sourceSets(":app").getValue("main").roots.map { it.directory.value to it.kind.value }
        assertEquals(
            listOf(
                "app/src/main/AndroidManifest.xml" to "android.manifest",
                "app/src/main/kotlin" to "kotlin",
                "app/src/main/res" to "android.res",
            ),
            roots,
        )
    }

    @Test
    fun `the flavor dimensions of the build are a module attribute in the order they are declared`() {
        assertEquals("tier", app.attributes["android.flavorDimensions"])
        val json = """[{"schema":1,"path":":app","dir":"/w/app","buildDir":"/w/app/build",
            "build":{"name":"w","rootDir":"/w"},"plugins":["com.android.application"],
            "android":{"kind":"dsl","buildFeatures":["buildConfig"],"flavorDimensions":["tier","store"]}}]"""
        val attributes = ModuleAttributes.of(ModelDocuments.parse(json).single())
        assertEquals("tier,store", attributes["android.flavorDimensions"])
    }

    @Test
    fun `a build without flavor dimensions has no such attribute`() {
        val json = """[{"schema":1,"path":":lib","dir":"/w/lib","buildDir":"/w/lib/build",
            "build":{"name":"w","rootDir":"/w"},"android":{"kind":"dsl","flavorDimensions":[]}}]"""
        assertEquals(null, ModuleAttributes.of(ModelDocuments.parse(json).single())["android.flavorDimensions"])
    }
}
