package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.analyzer.gradle.statics.StaticWorkspace
import io.github.krosstack.kscope.analyzer.gradle.statics.edgesOf
import io.github.krosstack.kscope.analyzer.gradle.statics.module
import io.github.krosstack.kscope.analyzer.gradle.statics.problems
import io.github.krosstack.kscope.analyzer.gradle.statics.sourceSets
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConventionStaticTest {
    private val tree = StaticWorkspace("conventions")
    private val provided = tree.provided()

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    private fun coordinatesOf(path: String) = provided.edgesOf(path).map { edge ->
        val target = (edge.resolution as Resolution.Resolved).target as NodeRef.OfExternal
        target.coordinate to edge.attributes["configuration"]
    }.sortedBy { it.first }

    @Test
    fun `a convention plugin contributes its plugins and keeps its own id`() {
        val lib = provided.module(":lib")
        assertEquals(
            listOf("acme.jvm.library", "java", "java-library", "org.jetbrains.kotlin.jvm"),
            lib.plugins,
        )
        assertEquals(setOf(ModuleTrait.KOTLIN_JVM, ModuleTrait.JAVA), lib.traits)
        assertEquals(Certainty.HEURISTIC, lib.certainty)
    }

    @Test
    fun `dependencies of a convention plugin are heuristic edges of the module that applies it`() {
        assertEquals(
            listOf(
                "maven:junit:junit" to "testImplementation",
                "maven:org.jetbrains.kotlin:kotlin-test" to "testImplementation",
            ),
            coordinatesOf(":lib"),
        )
        val junit = provided.edgesOf(":lib").first { it.attributes["version"] == "4.13.2" }
        assertEquals(Certainty.HEURISTIC, (junit.resolution as Resolution.Resolved).certainty)
        assertEquals("build-logic/src/main/kotlin/acme.jvm.library.gradle.kts", junit.site?.artifact?.path?.value)
    }

    @Test
    fun `a catalog entry read by name in a convention plugin is referenced from the plugin script`() {
        val references = provided.facts.filterIsInstance<Fact.OfRelationship>().map { it.value }
        val script = "build-logic/src/main/kotlin/acme.jvm.library.gradle.kts"
        val junit = references.single { it.site?.artifact?.path?.value == script }
        assertEquals("catalog.library", junit.subKind)
        assertEquals(Certainty.HEURISTIC, (junit.resolution as Resolution.Resolved).certainty)
    }

    @Test
    fun `convention plugins that apply convention plugins are expanded once`() {
        val android = provided.module(":android")
        assertEquals(
            listOf(
                "acme.android.library",
                "acme.base",
                "com.android.library",
                "org.jetbrains.kotlin.plugin.serialization",
            ),
            android.plugins,
        )
        assertEquals(listOf("maven:org.slf4j:slf4j-api" to "implementation"), coordinatesOf(":android"))
        assertEquals("true", android.attributes["kotlin.plugin.serialization"])
    }

    @Test
    fun `the android block of a convention plugin and the module's own block are merged`() {
        val android = provided.module(":android")
        assertEquals("acme.android", android.androidNamespace)
        assertEquals("36", android.attributes["android.compileSdk"])
        assertEquals(setOf(ModuleTrait.ANDROID_LIBRARY), android.traits)
        assertEquals(listOf("main"), provided.sourceSets(":android").keys.toList())
    }

    @Test
    fun `a plugin that is not analysed is an information and a module without convention plugins stays exact`() {
        val info = provided.problems("gradle.static.plugin-not-analysed").single()
        assertEquals(DiagnosticSeverity.INFO, info.severity)
        assertTrue("com.example.unknown" in info.message)
        assertEquals("app/build.gradle.kts", info.location?.artifact?.path?.value)
        assertEquals(Certainty.EXACT, provided.module(":app").certainty)
        assertEquals(listOf("com.example.unknown", "java", "java-library"), provided.module(":app").plugins)
    }

    @Test
    fun `the build logic of the included build is a module of its own`() {
        val logic = provided.module(":", "tasktide//build-logic")
        assertEquals(setOf(ModuleTrait.BUILD_LOGIC), logic.traits)
        assertEquals(listOf("main"), provided.sourceSets(":", "tasktide//build-logic").keys.toList())
    }
}
