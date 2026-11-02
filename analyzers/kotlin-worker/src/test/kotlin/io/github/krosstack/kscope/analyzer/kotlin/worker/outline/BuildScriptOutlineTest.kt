package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildScriptOutlineTest {
    private val fixture = OutlineFixture()
    private val app = fixture.ofSample("scripts/app/build.gradle.kts").calls
    private val library = fixture.ofSample("scripts/lib/build.gradle.kts").calls

    @AfterTest
    fun closeFixture() = fixture.close()

    private fun sourceSets(): List<ScriptCall> =
        library.single { it.name == "kotlin" }.body.single { it.name == "sourceSets" }.body

    @Test
    fun `plugins are plain calls and aliases keep the dotted accessor`() {
        val plugins = app.single { it.name == "plugins" }
        val aliases = plugins.body.named("alias")
        assertEquals(listOf("libs.plugins.android.application"), aliases[0].arguments.map { it.expression })
        assertEquals(listOf("kapt"), plugins.body.named("kotlin").map { it.literals().single() })
        assertEquals(listOf("kotlin-dsl", "application"), plugins.body.takeLast(2).map { it.name })
    }

    @Test
    fun `infix modifiers of a plugin become assignments`() {
        val plugins = app.single { it.name == "plugins" }
        assertEquals(mapOf("apply" to "false"), plugins.body.named("alias")[1].assignments)
        val serialization = plugins.body.first { it.literals() == listOf("org.jetbrains.kotlin.plugin.serialization") }
        assertEquals(mapOf("version" to "\"2.0.0\"", "apply" to "false"), serialization.assignments)
    }

    @Test
    fun `assignments inside lambdas are collected on the enclosing call`() {
        val android = app.single { it.name == "android" }
        assertEquals(mapOf("namespace" to "\"com.example.app\"", "compileSdk" to "36"), android.assignments)
        assertEquals("\"1.0\"", android.body.single { it.name == "defaultConfig" }.assignments["versionName"])
        val features = android.body.single { it.name == "buildFeatures" }
        assertEquals(mapOf("viewBinding" to "true", "compose" to "false"), features.assignments)
    }

    @Test
    fun `compound assignments are calls named by their operator`() {
        val dimensions = app.single { it.name == "android" }.body.single { it.name == "+=" }
        assertEquals("flavorDimensions", dimensions.receiver)
        assertEquals(listOf("tier"), dimensions.literals())
    }

    @Test
    fun `dependencies cover project, typed accessors, catalog aliases and string invoked configurations`() {
        val dependencies = app.single { it.name == "dependencies" }.body
        val implementations = dependencies.named("implementation").map { it.arguments.single().expression }
        val expected = listOf("project(\":core:model\")", "projects.core.legacy", "projects.feature.tasks")
        assertEquals(expected, implementations.take(3))
        assertEquals(listOf("libs.room.compiler"), dependencies.named("kapt").map { it.arguments.single().expression })
        assertEquals(listOf("junit:junit:4.13.2"), dependencies.named("androidTestImplementation").single().literals())
    }

    @Test
    fun `source sets declared by delegation carry their name as the first argument and their dependsOn calls`() {
        val desktop = sourceSets().named("getting").single { it.literals().first() == "desktopMain" }
        assertEquals(ScriptArgument(null, "desktopMain", "desktopMain"), desktop.arguments.first())
        val dependsOn = desktop.body.single()
        assertEquals("dependsOn", dependsOn.name)
        assertEquals("commonMain", dependsOn.arguments.single().expression)
        assertEquals("commonTest", sourceSets().named("creating").single().literals().first())
    }

    @Test
    fun `source set accessors with receivers keep the receiver text`() {
        val dependencies = sourceSets().single { it.name == "dependencies" && it.receiver == "commonMain" }
        assertEquals(listOf("libs.ktor.client"), dependencies.body.map { it.arguments.single().expression })
        val androidMain = sourceSets().single { it.name == "androidMain" }
        assertEquals("commonMain", androidMain.body.single().arguments.single().expression)
    }

    @Test
    fun `top level assignments become calls named by the assignment operator`() {
        val version = library.single { it.name == "=" && it.receiver == "version" }
        assertEquals(listOf(ScriptArgument(null, "1.2.3", "\"1.2.3\"")), version.arguments)
    }
}
