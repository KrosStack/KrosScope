package io.github.krosstack.kscope.analyzer.gradle

import io.github.krosstack.kscope.analyzer.gradle.mapping.SampleModel
import io.github.krosstack.kscope.model.ModuleTrait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidFactsTest {
    private val sample = SampleModel.load("android-app-flavors.json")

    @Test
    fun `build features, compile sdk and build config fields become module attributes`() {
        val expected = mapOf(
            "android.buildConfigField.MAX_TASKS" to "int",
            "android.buildConfigField.SYNC_ENDPOINT" to "String",
            "android.buildConfigField.VERBOSE_LOGGING" to "boolean",
            "android.buildFeatures.buildConfig" to "true",
            "android.buildFeatures.compose" to "true",
            "android.buildFeatures.viewBinding" to "true",
            "android.compileSdk" to "36",
            "android.flavorDimensions" to "tier",
            "kotlin.plugin.compose" to "true",
        )
        val attributes = sample.module(":app").attributes
        assertEquals(expected, attributes)
        assertEquals(expected.keys.sorted(), attributes.keys.toList())
    }

    @Test
    fun `every variant component of the application contributes its classpath to the source sets it uses`() {
        val main = sample.classpath(":app@main")
        assertTrue(main.any { it.endsWith("android.jar") })
        assertTrue(sample.classpath(":app@test").isNotEmpty())
        assertTrue(sample.classpath(":app@androidTest").isNotEmpty())
        assertEquals(main, main.distinct())
    }

    @Test
    fun `an android library has debug and release variants`() {
        val tasks = sample.module(":feature:tasks")
        assertEquals(listOf("debug", "release"), sample.model.variants.filter { it.module == tasks.id }.map { it.name })
        assertEquals(setOf(ModuleTrait.ANDROID_LIBRARY, ModuleTrait.KOTLIN_ANDROID), tasks.traits)
    }

    @Test
    fun `a service module keeps its aidl directory and its parcelize fact`() {
        val aidl = sample.sourceSets(":sync:service").getValue("main").roots.single { it.kind.value == "aidl" }
        assertEquals("sync/service/src/main/aidl", aidl.directory.value)
        assertEquals("true", sample.module(":sync:service").attributes["kotlin.plugin.parcelize"])
    }

    @Test
    fun `dependencies keep the configuration they were declared in`() {
        val dependencies = sample.moduleDependencies(":app")
        assertTrue(":core:model" to "implementation" in dependencies)
        assertTrue(":feature:tasks" to "implementation" in dependencies)
        assertTrue(sample.externalDependencies(":app").any { it.coordinate.startsWith("maven:androidx.") })
    }
}
