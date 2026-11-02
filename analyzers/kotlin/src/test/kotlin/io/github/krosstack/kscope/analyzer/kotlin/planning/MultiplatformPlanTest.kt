package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A Kotlin Multiplatform library with Android and JVM targets, used by an Android app and by a JVM module. */
class MultiplatformPlanTest {
    private val model = model {
        module(":lib") {
            target("jvm", Platform.JVM)
            target("android", Platform.ANDROID)
            sourceSet("commonMain", targets = listOf("jvm", "android"))
            sourceSet("jvmMain", dependsOn = listOf("commonMain"), targets = listOf("jvm"))
            sourceSet("androidMain", dependsOn = listOf("commonMain"), targets = listOf("android"))
            sourceSet("commonTest", targets = listOf("jvm", "android"))
            sourceSet("jvmTest", dependsOn = listOf("commonTest"), targets = listOf("jvm"))
        }
        module(":app") {
            target("android", Platform.ANDROID)
            variant("debug", "debug")
            variant("release", "release")
            sourceSet("main", targets = listOf("android"), variants = listOf("debug", "release"))
            sourceSet("debug", targets = listOf("android"), variants = listOf("debug"))
            sourceSet("release", targets = listOf("android"), variants = listOf("release"))
        }
        module(":tool") {
            target("jvm", Platform.JVM)
            sourceSet("main", targets = listOf("jvm"))
            sourceSet("test", targets = listOf("jvm"))
        }
        dependency(from = ":app", to = ":lib")
        dependency(from = ":tool", to = ":lib")
    }

    private val plan = model.planAll()
    private val common = moduleName(":lib", "commonMain")
    private val jvm = moduleName(":lib", "jvmMain")
    private val android = moduleName(":lib", "androidMain")

    @Test
    fun `every source set of a module without variants is its own analysis module`() {
        val names = plan.modules.filter { it.name.startsWith("repo//:lib@") }.map { it.name }
        assertEquals(
            listOf(android, common, moduleName(":lib", "commonTest"), jvm, moduleName(":lib", "jvmTest")),
            names,
        )
    }

    @Test
    fun `platforms follow the targets of the source set`() {
        assertEquals(Platform.COMMON, plan.module(common).platform)
        assertEquals(Platform.JVM, plan.module(jvm).platform)
        assertEquals(Platform.ANDROID, plan.module(android).platform)
        assertEquals(Platform.COMMON, plan.module(moduleName(":lib", "commonTest")).platform)
    }

    @Test
    fun `refinement edges are kept`() {
        assertEquals(listOf(common), plan.module(jvm).dependsOn)
        assertEquals(listOf(common), plan.module(android).dependsOn)
        assertEquals(emptyList(), plan.module(common).dependsOn)
    }

    @Test
    fun `an android app sees the android and common parts of the library`() {
        val app = plan.module(groupName(":app", "debug"))
        assertEquals(listOf(android, common), app.dependencies)
    }

    @Test
    fun `a jvm module sees the jvm and common parts of the library`() {
        assertEquals(listOf(common, jvm), plan.module(moduleName(":tool", "main")).dependencies)
    }

    @Test
    fun `the common part of a library depends on nothing outside itself`() {
        assertEquals(emptyList(), plan.module(common).dependencies)
    }

    @Test
    fun `a multiplatform test source set sees the internals of its main counterpart`() {
        val jvmTest = plan.module(moduleName(":lib", "jvmTest"))
        assertEquals(listOf(jvm), jvmTest.friends)
        assertEquals(listOf(moduleName(":lib", "commonTest")), jvmTest.dependsOn)
        assertEquals(listOf(common, jvm), jvmTest.dependencies)
        assertEquals(listOf(common), plan.module(moduleName(":lib", "commonTest")).friends)
    }

    @Test
    fun `only requested source sets emit facts and the others are context`() {
        val partial = model.plan(listOf(sourceSetId(":app", "main")))
        val emitting = partial.modules.flatMap { it.sourceSets }.filter { it.emitFactsFor }.map { it.sourceSet }
        assertEquals(listOf(sourceSetId(":app", "main")), emitting)
        assertTrue(partial.modules.any { it.name == common })
    }

    @Test
    fun `modules the requested source sets do not depend on are left out`() {
        val partial = model.plan(listOf(sourceSetId(":lib", "jvmMain")))
        assertEquals(setOf("repo//:lib"), partial.modules.flatMap { it.sourceSets }.map { it.module.value }.toSet())
        assertFalse(partial.dependenciesAssumed)
    }

    @Test
    fun `a source set without files is not a module and refinement leads through it`() {
        val partial = model.plan(model.sourceSets.map { it.id }, without = setOf(sourceSetId(":lib", "commonMain")))
        assertEquals(emptyList(), partial.modules.filter { it.name == common })
        assertEquals(emptyList(), partial.module(jvm).dependsOn)
    }

    @Test
    fun `source sets between a module and the source set it refines are bridged`() {
        val layered = model {
            module(":lib") {
                target("jvm", Platform.JVM)
                target("android", Platform.ANDROID)
                sourceSet("commonMain", targets = listOf("jvm", "android"))
                sourceSet("sharedMain", dependsOn = listOf("commonMain"), targets = listOf("jvm", "android"))
                sourceSet("jvmMain", dependsOn = listOf("sharedMain"), targets = listOf("jvm"))
            }
        }
        val plan = layered.plan(layered.sourceSets.map { it.id }, without = setOf(sourceSetId(":lib", "sharedMain")))
        assertEquals(listOf(moduleName(":lib", "commonMain")), plan.module(moduleName(":lib", "jvmMain")).dependsOn)
    }
}
