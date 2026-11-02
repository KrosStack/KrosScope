package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssumedDependencyPlanTest {
    private val structural = model {
        module(":app") {
            sourceSet("main")
            sourceSet("test")
        }
        module(":lib") {
            sourceSet("main")
            sourceSet("test")
        }
    }

    @Test
    fun `without dependency information every module sees every other main source set`() {
        val plan = structural.planAll()
        assertTrue(plan.dependenciesAssumed)
        val app = plan.module(moduleName(":app", "main"))
        val lib = plan.module(moduleName(":lib", "main"))
        assertEquals(listOf(moduleName(":lib", "main")), app.dependencies)
        assertEquals(listOf(moduleName(":app", "main")), lib.dependencies)
    }

    @Test
    fun `tests see all main source sets and are never seen`() {
        val plan = structural.planAll()
        val test = plan.module(moduleName(":app", "test"))
        assertEquals(listOf(moduleName(":app", "main"), moduleName(":lib", "main")), test.dependencies)
        assertEquals(listOf(moduleName(":app", "main")), test.friends)
        assertTrue(plan.modules.none { module -> module.dependencies.any { it.endsWith("@test") } })
    }

    @Test
    fun `every source set of the workspace is analysed even when only one is requested`() {
        val plan = structural.plan(listOf(sourceSetId(":app", "main")))
        val emitting = plan.modules.flatMap { it.sourceSets }.filter { it.emitFactsFor }.map { it.sourceSet }
        assertEquals(4, plan.modules.size)
        assertEquals(listOf(sourceSetId(":app", "main")), emitting)
    }

    @Test
    fun `common code sees only common code and jvm and android see each other`() {
        val plan = model {
            module(":shared") {
                target("jvm", Platform.JVM)
                target("ios", Platform.NATIVE)
                sourceSet("commonMain", targets = listOf("jvm", "ios"))
            }
            module(":jvm") {
                target("jvm", Platform.JVM)
                sourceSet("main", targets = listOf("jvm"))
            }
            module(":android") {
                target("android", Platform.ANDROID)
                sourceSet("main", targets = listOf("android"))
            }
            module(":native") {
                target("ios", Platform.NATIVE)
                sourceSet("main", targets = listOf("ios"))
            }
        }.planAll()
        val shared = moduleName(":shared", "commonMain")
        assertEquals(emptyList(), plan.module(shared).dependencies)
        val jvm = plan.module(moduleName(":jvm", "main"))
        val android = plan.module(moduleName(":android", "main"))
        assertEquals(listOf(moduleName(":android", "main"), shared), jvm.dependencies)
        assertEquals(listOf(moduleName(":jvm", "main"), shared), android.dependencies)
        assertEquals(listOf(shared), plan.module(moduleName(":native", "main")).dependencies)
    }
}
