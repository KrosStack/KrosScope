package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.Platform
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A JVM module that depends on a Kotlin Multiplatform module whose JVM target has no source set of its own, which is
 * common for libraries that keep all their code in `commonMain`: the JVM target compiles `commonMain` alone.
 */
class JvmConsumerOfMultiplatformPlanTest {
    private val model = model {
        module(":shared") {
            target("jvm", Platform.JVM)
            target("ios", Platform.NATIVE)
            sourceSet("commonMain", targets = listOf("jvm", "ios"))
            sourceSet("jvmMain", dependsOn = listOf("commonMain"), targets = listOf("jvm"))
            sourceSet("iosMain", dependsOn = listOf("commonMain"), targets = listOf("ios"))
        }
        module(":server") {
            target("jvm", Platform.JVM)
            sourceSet("main", targets = listOf("jvm"))
            sourceSet("test", targets = listOf("jvm"))
        }
        dependency(from = ":server", to = ":shared")
    }

    private val common = moduleName(":shared", "commonMain")
    private val jvm = moduleName(":shared", "jvmMain")

    @Test
    fun `a jvm module sees the common source set of a dependency that has no jvm source set with files`() {
        val plan = model.plan(model.sourceSets.map { it.id }, without = setOf(sourceSetId(":shared", "jvmMain")))
        assertEquals(listOf(common), plan.module(moduleName(":server", "main")).dependencies)
        assertEquals(
            listOf(moduleName(":server", "main"), common),
            plan.module(moduleName(":server", "test")).dependencies,
        )
    }

    @Test
    fun `a jvm module sees the jvm source set of a dependency and, through it, the common one`() {
        val plan = model.planAll()
        assertEquals(listOf(common, jvm), plan.module(moduleName(":server", "main")).dependencies)
    }
}
