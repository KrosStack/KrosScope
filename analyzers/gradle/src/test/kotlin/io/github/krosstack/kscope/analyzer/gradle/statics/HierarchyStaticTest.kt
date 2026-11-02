package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.Platform
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HierarchyStaticTest {
    private val tree = StaticWorkspace("kmp-native")
    private val native = tree.provided()
    private val edges = native.dependsOnOf(":ios")

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    @Test
    fun `the default hierarchy template shares source sets between two or more targets`() {
        assertEquals(
            listOf(
                "appleMain", "appleTest", "commonMain", "commonTest", "iosArm64Main", "iosMain",
                "iosSimulatorArm64Main", "iosTest", "iosX64Main", "jsMain", "jvmMain", "linuxX64Main", "nativeMain",
                "nativeTest", "wasmJsMain", "webMain",
            ),
            native.sourceSets(":ios").keys.toList(),
        )
    }

    @Test
    fun `native targets chain through the groups of the template`() {
        assertEquals(listOf("appleMain"), edges.getValue("iosMain"))
        assertEquals(listOf("nativeMain"), edges.getValue("appleMain"))
        assertEquals(listOf("commonMain"), edges.getValue("nativeMain"))
        assertEquals(listOf("iosMain"), edges.getValue("iosArm64Main"))
        assertEquals(listOf("nativeMain"), edges.getValue("linuxX64Main"))
        assertEquals(listOf("commonMain"), edges.getValue("jvmMain"))
    }

    @Test
    fun `js and wasm targets share the web source set`() {
        assertEquals(listOf("webMain"), edges.getValue("jsMain"))
        assertEquals(listOf("webMain"), edges.getValue("wasmJsMain"))
        assertEquals(listOf("commonMain"), edges.getValue("webMain"))
        assertTrue("wasmMain" !in edges)
    }

    @Test
    fun `test source sets bring the test source sets above them`() {
        assertEquals(listOf("appleTest"), edges.getValue("iosTest"))
        assertEquals(listOf("nativeTest"), edges.getValue("appleTest"))
        assertEquals(listOf("commonTest"), edges.getValue("nativeTest"))
    }

    @Test
    fun `targets have the platform of their kind`() {
        val targets = native.model.targets.filter { it.module.value == "tasktide//:ios" }
        val platforms = targets.associate { it.name to it.platform }
        assertEquals(Platform.JVM, platforms.getValue("jvm"))
        assertEquals(Platform.NATIVE, platforms.getValue("iosArm64"))
        assertEquals(Platform.JS, platforms.getValue("js"))
        assertEquals(Platform.WASM, platforms.getValue("wasmJs"))
    }

    @Test
    fun `a customised hierarchy is a warning and the default edges are kept`() {
        val warning = native.problems("gradle.static.custom-hierarchy-not-analysed").single()
        assertEquals(DiagnosticSeverity.WARNING, warning.severity)
        assertEquals("custom/build.gradle.kts", warning.location?.artifact?.path?.value)
        val custom = native.dependsOnOf(":custom")
        assertEquals(listOf("iosMain"), custom.getValue("iosArm64Main"))
        assertEquals(listOf("appleMain"), custom.getValue("iosMain"))
    }

    @Test
    fun `an android library module with the kotlin plugin has variants and an android source set for them`() {
        val classic = native.module(":classic")
        assertEquals(setOf(ModuleTrait.ANDROID_LIBRARY, ModuleTrait.KOTLIN_MULTIPLATFORM), classic.traits)
        assertEquals(listOf("android", "jvm"), native.model.targets.filter { it.module == classic.id }.map { it.name })
        val variants = native.model.variants.filter { it.module == classic.id }
        assertEquals(listOf("debug", "release"), variants.map { it.name })
        assertEquals(listOf("debug", "release"), native.variantsOf(":classic", "androidMain"))
    }
}
