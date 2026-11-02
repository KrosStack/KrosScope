package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.spi.SyntheticSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PlanDetailsTest {
    private val model = model {
        module(":lib") {
            target("jvm", Platform.JVM)
            sourceSet("main", targets = listOf("jvm"))
        }
    }
    private val main = sourceSetId(":lib", "main")

    @Test
    fun `a source set without targets is a jvm module`() {
        val untargeted = model { module(":lib") { sourceSet("main") } }.planAll()
        assertEquals(Platform.JVM, untargeted.module(moduleName(":lib", "main")).platform)
    }

    @Test
    fun `an android only source set stays android`() {
        val android = model {
            module(":lib") {
                target("android", Platform.ANDROID)
                sourceSet("main", targets = listOf("android"))
            }
        }.planAll()
        assertEquals(Platform.ANDROID, android.module(moduleName(":lib", "main")).platform)
    }

    @Test
    fun `two targets of one platform are that platform`() {
        val twoJvm = model {
            module(":lib") {
                target("jvm", Platform.JVM)
                target("desktop", Platform.JVM)
                sourceSet("main", targets = listOf("jvm", "desktop"))
            }
        }.planAll()
        assertEquals(Platform.JVM, twoJvm.module(moduleName(":lib", "main")).platform)
    }

    @Test
    fun `synthetic sources and classpaths are attached to their source set`() {
        val stub = SyntheticSource("gen/R.kt", "kotlin", "object R", main, "android.resources")
        val plan = model.plan(listOf(main), synthetic = listOf(stub), classpaths = mapOf(main to listOf("/libs/a.jar")))
        val module = plan.module(moduleName(":lib", "main"))
        assertEquals(listOf(stub), module.sourceSets.single().syntheticSources)
        assertEquals(listOf("/libs/a.jar"), module.classpath)
    }

    @Test
    fun `a source set that only has synthetic sources is still analysed`() {
        val stub = SyntheticSource("gen/R.kt", "kotlin", "object R", main, "android.resources")
        val plan = model.plan(listOf(main), without = setOf(main), synthetic = listOf(stub))
        assertEquals(listOf(moduleName(":lib", "main")), plan.modules.map { it.name })
    }

    @Test
    fun `the fingerprint of a source set follows its files and synthetic sources`() {
        val before = model.plan(listOf(main)).fingerprints.getValue(main)
        val stub = SyntheticSource("gen/R.kt", "kotlin", "object R", main, "android.resources")
        val withStub = model.plan(listOf(main), synthetic = listOf(stub)).fingerprints.getValue(main)
        assertNotEquals(before, withStub)
        assertEquals(before, model.plan(listOf(main)).fingerprints.getValue(main))
    }

    @Test
    fun `files are given in path order`() {
        val plan = model.planAll()
        val files = plan.module(moduleName(":lib", "main")).sourceSets.single().files
        assertEquals(files.map { it.ref.path.value }.sorted(), files.map { it.ref.path.value })
    }
}
