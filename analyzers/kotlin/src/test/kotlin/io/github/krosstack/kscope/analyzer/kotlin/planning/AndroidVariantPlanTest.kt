package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.Platform
import kotlin.test.Test
import kotlin.test.assertEquals

/** An Android application with the flavors demo and full and the build types debug and release. */
class AndroidVariantPlanTest {
    private val allVariants = listOf("demoDebug", "demoRelease", "fullDebug", "fullRelease")

    private val model = model {
        module(":app") {
            target("android", Platform.ANDROID)
            variant("demoDebug", "debug", "demo")
            variant("demoRelease", "release", "demo")
            variant("fullDebug", "debug", "full")
            variant("fullRelease", "release", "full")
            sourceSet("main", targets = listOf("android"), variants = allVariants)
            sourceSet("demo", targets = listOf("android"), variants = listOf("demoDebug", "demoRelease"))
            sourceSet("full", targets = listOf("android"), variants = listOf("fullDebug", "fullRelease"))
            sourceSet("debug", targets = listOf("android"), variants = listOf("demoDebug", "fullDebug"))
            sourceSet("release", targets = listOf("android"), variants = listOf("demoRelease", "fullRelease"))
            sourceSet("demoDebug", targets = listOf("android"), variants = listOf("demoDebug"))
            sourceSet("fullRelease", targets = listOf("android"), variants = listOf("fullRelease"))
            sourceSet("test", targets = listOf("android"), variants = allVariants)
            sourceSet("testDebug", targets = listOf("android"), variants = listOf("demoDebug", "fullDebug"))
            sourceSet("androidTest", targets = listOf("android"), variants = allVariants)
        }
        module(":consumer") {
            target("android", Platform.ANDROID)
            sourceSet("main", targets = listOf("android"))
        }
        dependency(from = ":consumer", to = ":app")
    }

    private val plan = model.planAll()
    private val group = groupName(":app", "demoDebug")

    @Test
    fun `the main source sets of the first debug variant compile together`() {
        val grouped = plan.module(group).sourceSets.map { it.sourceSet }
        val expected = listOf("debug", "demo", "demoDebug", "main").map { sourceSetId(":app", it) }
        assertEquals(expected, grouped)
        assertEquals(Platform.ANDROID, plan.module(group).platform)
    }

    @Test
    fun `source sets of other variants and the test source sets are modules of their own`() {
        val names = plan.modules.map { it.name }.filter { it != group && it.startsWith("repo//:app") }
        val expected = listOf("androidTest", "full", "fullRelease", "release", "test", "testDebug")
            .map { moduleName(":app", it) }
        assertEquals(expected, names)
    }

    @Test
    fun `a variant source set outside the default group depends on the default group`() {
        listOf("full", "release", "fullRelease").forEach { name ->
            assertEquals(listOf(group), plan.module(moduleName(":app", name)).dependencies, name)
        }
        assertEquals(emptyList(), plan.module(moduleName(":app", "full")).friends)
    }

    @Test
    fun `android test source sets see the default group and nothing outside it`() {
        listOf("test", "testDebug", "androidTest").forEach { name ->
            val module = plan.module(moduleName(":app", name))
            assertEquals(listOf(group), module.friends, name)
            assertEquals(listOf(group), module.dependencies, name)
        }
    }

    @Test
    fun `a module that uses the app sees its default group only`() {
        assertEquals(listOf(group), plan.module(moduleName(":consumer", "main")).dependencies)
    }

    @Test
    fun `without a debug variant the alphabetically first variant is the default`() {
        val staging = model {
            module(":app") {
                target("android", Platform.ANDROID)
                variant("stagingRelease", "release")
                variant("alphaRelease", "release")
                sourceSet("main", targets = listOf("android"), variants = listOf("stagingRelease", "alphaRelease"))
                sourceSet("alpha", targets = listOf("android"), variants = listOf("alphaRelease"))
            }
        }.planAll()
        assertEquals(listOf(groupName(":app", "alphaRelease")), staging.modules.map { it.name })
    }

    @Test
    fun `a module with variants whose source sets name none of them keeps one module per source set`() {
        val unlinked = model {
            module(":app") {
                target("android", Platform.ANDROID)
                variant("debug", "debug")
                sourceSet("main", targets = listOf("android"))
                sourceSet("debug", targets = listOf("android"))
            }
        }.planAll()
        assertEquals(listOf(moduleName(":app", "debug"), moduleName(":app", "main")), unlinked.modules.map { it.name })
    }

    @Test
    fun `a default group of one source set is named after that source set`() {
        val single = model {
            module(":app") {
                target("android", Platform.ANDROID)
                variant("debug", "debug")
                sourceSet("main", targets = listOf("android"), variants = listOf("debug"))
            }
        }.planAll()
        assertEquals(listOf(moduleName(":app", "main")), single.modules.map { it.name })
    }
}
