package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.analyzer.gradle.statics.ROOT
import io.github.krosstack.kscope.analyzer.gradle.statics.module
import io.github.krosstack.kscope.analyzer.gradle.statics.sourceSets
import io.github.krosstack.kscope.model.ModuleTrait
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ClassPluginAndroidStaticTest {
    private val tree = ClassConventionTree()
    private val provided = tree.provided

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    @Test
    fun `a plugin class gives its module the Android trait and the namespace of its own script`() {
        val lib = provided.module(":lib")
        assertEquals(setOf(ModuleTrait.ANDROID_LIBRARY), lib.traits)
        assertEquals("acme.lib", lib.androidNamespace)
    }

    @Test
    fun `the android configuration of a class and of the helper it calls is read`() {
        val lib = provided.module(":lib")
        assertEquals("35", lib.attributes["android.compileSdk"])
        assertEquals("true", lib.attributes["android.buildFeatures.buildConfig"])
        assertEquals(listOf("main"), provided.sourceSets(":lib").keys.toList())
        val variants = provided.model.variants.filter { it.module.value == ROOT + ":lib" }
        assertEquals(listOf("debug", "release"), variants.map { it.buildType })
    }

    @Test
    fun `an extension read into a local is configured by a helper, also through its members`() {
        val ui = provided.module(":ui")
        assertEquals("true", ui.attributes["android.buildFeatures.compose"])
        assertEquals("true", ui.attributes["android.buildFeatures.viewBinding"])
        assertEquals("true", ui.attributes["kotlin.plugin.compose"])
        assertEquals("acme.ui", ui.androidNamespace)
    }

    @Test
    fun `an application extension read by type and configured by apply gives an application`() {
        val app = provided.module(":app")
        assertEquals(setOf(ModuleTrait.ANDROID_APPLICATION), app.traits)
        assertEquals("36", app.attributes["android.compileSdk"])
        assertEquals("acme.app", app.androidNamespace)
    }

    @Test
    fun `the kotlin multiplatform extension of a class gives targets`() {
        val targets = provided.model.targets.filter { it.module.value == ROOT + ":kmp" }
        assertEquals(listOf("iosArm64", "jvm"), targets.map { it.name }.sorted())
        assertEquals(listOf("acme.kmp.library", "org.jetbrains.kotlin.multiplatform"), provided.module(":kmp").plugins)
    }
}
