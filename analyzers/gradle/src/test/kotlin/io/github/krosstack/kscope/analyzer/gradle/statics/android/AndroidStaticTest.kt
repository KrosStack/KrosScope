package io.github.krosstack.kscope.analyzer.gradle.statics.android

import io.github.krosstack.kscope.analyzer.gradle.statics.StaticWorkspace
import io.github.krosstack.kscope.analyzer.gradle.statics.module
import io.github.krosstack.kscope.analyzer.gradle.statics.sourceSets
import io.github.krosstack.kscope.analyzer.gradle.statics.variantsOf
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.SourceSetRole
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidStaticTest {
    private val tree = StaticWorkspace("android-kotlin")
    private val provided = tree.provided()

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    @Test
    fun `an application has the application trait, its namespace and the plugins it applies`() {
        val app = provided.module(":app")
        assertEquals(setOf(ModuleTrait.ANDROID_APPLICATION), app.traits)
        assertEquals("sample.app", app.androidNamespace)
        assertEquals(
            listOf(
                "com.android.application",
                "org.jetbrains.kotlin.plugin.compose",
                "org.jetbrains.kotlin.plugin.parcelize",
            ),
            app.plugins,
        )
        assertEquals(Certainty.EXACT, app.certainty)
    }

    @Test
    fun `compile sdk, build features, build config fields and plugin flags become attributes`() {
        assertEquals(
            mapOf(
                "android.buildConfigField.MAX_TASKS" to "int",
                "android.buildConfigField.SYNC_ENDPOINT" to "String",
                "android.buildConfigField.VERBOSE_LOGGING" to "boolean",
                "android.buildFeatures.buildConfig" to "true",
                "android.buildFeatures.compose" to "true",
                "android.buildFeatures.viewBinding" to "true",
                "android.compileSdk" to "36",
                "android.flavorDimensions" to "tier",
                "kotlin.plugin.compose" to "true",
                "kotlin.plugin.parcelize" to "true",
            ),
            provided.module(":app").attributes,
        )
    }

    @Test
    fun `variants are the flavor and build type combinations in the name rule of the plugin`() {
        val variants = provided.model.variants.filter { it.module.value == "tasktide//:app" }
        assertEquals(
            listOf("demoDebug", "demoRelease", "demoStaging", "fullDebug", "fullRelease", "fullStaging"),
            variants.map { it.name },
        )
        val demoStaging = variants.first { it.name == "demoStaging" }
        assertEquals("staging", demoStaging.buildType)
        assertEquals(listOf("demo"), demoStaging.flavors)
        assertEquals("tasktide//:app#variant/demoStaging", demoStaging.id.value)
    }

    @Test
    fun `source sets exist when they have a directory and main always does`() {
        assertEquals(
            listOf("androidTest", "debug", "demo", "demoDebug", "main", "staging", "test", "testDemoDebug"),
            provided.sourceSets(":app").keys.toList(),
        )
    }

    @Test
    fun `source sets belong to the variants they are assembled into`() {
        assertEquals(6, provided.variantsOf(":app", "main").size)
        assertEquals(listOf("demoDebug", "demoRelease", "demoStaging"), provided.variantsOf(":app", "demo"))
        assertEquals(listOf("demoDebug", "fullDebug"), provided.variantsOf(":app", "debug"))
        assertEquals(listOf("demoStaging", "fullStaging"), provided.variantsOf(":app", "staging"))
        assertEquals(listOf("demoDebug"), provided.variantsOf(":app", "demoDebug"))
    }

    @Test
    fun `test source sets belong to the variants of the debug build type`() {
        assertEquals(listOf("demoDebug", "fullDebug"), provided.variantsOf(":app", "test"))
        assertEquals(listOf("demoDebug", "fullDebug"), provided.variantsOf(":app", "androidTest"))
        assertEquals(listOf("demoDebug"), provided.variantsOf(":app", "testDemoDebug"))
        val sourceSets = provided.sourceSets(":app")
        assertEquals(SourceSetRole.UNIT_TEST, sourceSets.getValue("test").role)
        assertEquals(SourceSetRole.INSTRUMENTED_TEST, sourceSets.getValue("androidTest").role)
        assertEquals(SourceSetRole.MAIN, sourceSets.getValue("demoDebug").role)
    }

    @Test
    fun `source sets of conventions are heuristic and all compile for the android target`() {
        val main = provided.sourceSets(":app").getValue("main")
        assertEquals(Certainty.HEURISTIC, main.certainty)
        assertEquals(listOf("tasktide//:app#target/android"), main.targets.map { it.value })
        val targets = provided.model.targets.filter { it.module.value == "tasktide//:app" }
        assertEquals(listOf("android" to Platform.ANDROID), targets.map { it.name to it.platform })
    }

    @Test
    fun `roots are found by convention and carry their kind`() {
        val roots = provided.sourceSets(":app").getValue("main").roots.map { it.directory.value to it.kind.value }
        assertEquals(
            listOf(
                "app/src/main/AndroidManifest.xml" to "android.manifest",
                "app/src/main/kotlin" to "kotlin",
                "app/src/main/res" to "android.res",
            ),
            roots,
        )
    }
}
