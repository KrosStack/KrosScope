package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.Platform
import kotlin.test.Test
import kotlin.test.assertEquals

class ExposedDependencyPlanTest {
    private fun library(): ModuleDsl.() -> Unit = {
        target("jvm", Platform.JVM)
        target("ios", Platform.NATIVE)
        sourceSet("commonMain", targets = listOf("jvm", "ios"))
        sourceSet("jvmMain", dependsOn = listOf("commonMain"), targets = listOf("jvm"))
        sourceSet("iosMain", dependsOn = listOf("commonMain"), targets = listOf("ios"))
    }

    @Test
    fun `an api dependency of one platform of a dependency reaches only the groups of that platform`() {
        val plan = model {
            module(":hidden") { sourceSet("main") }
            module(":lib", library())
            module(":app", library())
            dependency(from = ":lib", to = ":hidden", scope = "jvmMain", configuration = "jvmMainApi")
            dependency(from = ":app", to = ":lib")
        }.planAll()
        val common = moduleName(":lib", "commonMain")
        assertEquals(listOf(common), plan.module(moduleName(":app", "commonMain")).dependencies)
        assertEquals(
            listOf(common, moduleName(":lib", "iosMain")),
            plan.module(moduleName(":app", "iosMain")).dependencies,
        )
        assertEquals(
            listOf(moduleName(":hidden", "main"), common, moduleName(":lib", "jvmMain")),
            plan.module(moduleName(":app", "jvmMain")).dependencies,
        )
    }

    @Test
    fun `an api dependency of a source set without files reaches the groups of its platform`() {
        val built = model {
            module(":hidden") { sourceSet("main") }
            module(":lib", library())
            module(":app", library())
            dependency(from = ":lib", to = ":hidden", scope = "jvmMain", configuration = "jvmMainApi")
            dependency(from = ":app", to = ":lib")
        }
        val plan = built.plan(built.sourceSets.map { it.id }, without = setOf(sourceSetId(":lib", "jvmMain")))
        val common = moduleName(":lib", "commonMain")
        assertEquals(
            listOf(moduleName(":hidden", "main"), common),
            plan.module(moduleName(":app", "jvmMain")).dependencies,
        )
        assertEquals(listOf(common), plan.module(moduleName(":app", "commonMain")).dependencies)
    }

    @Test
    fun `a module without files exposes its common api dependency to every platform and its jvm one to jvm only`() {
        val built = model {
            module(":hidden") { sourceSet("main") }
            module(":jvmOnly") { sourceSet("main") }
            module(":lib", library())
            module(":app", library())
            dependency(from = ":lib", to = ":hidden", scope = "commonMain", configuration = "commonMainApi")
            dependency(from = ":lib", to = ":jvmOnly", scope = "jvmMain", configuration = "jvmMainApi")
            dependency(from = ":app", to = ":lib")
        }
        val withoutFiles = listOf("commonMain", "jvmMain", "iosMain").map { sourceSetId(":lib", it) }.toSet()
        val plan = built.plan(built.sourceSets.map { it.id }, without = withoutFiles)
        val hidden = moduleName(":hidden", "main")
        assertEquals(listOf(hidden), plan.module(moduleName(":app", "commonMain")).dependencies)
        assertEquals(listOf(hidden), plan.module(moduleName(":app", "iosMain")).dependencies)
        assertEquals(
            listOf(hidden, moduleName(":jvmOnly", "main")),
            plan.module(moduleName(":app", "jvmMain")).dependencies,
        )
    }

    @Test
    fun `a test api dependency of a dependency is not exposed to its consumers`() {
        val plan = model {
            module(":hidden") { sourceSet("main") }
            module(":lib") {
                sourceSet("main")
                sourceSet("test")
            }
            module(":app") { sourceSet("main") }
            dependency(from = ":lib", to = ":hidden", scope = "test", configuration = "testApi")
            dependency(from = ":app", to = ":lib")
        }.planAll()
        assertEquals(listOf(moduleName(":lib", "main")), plan.module(moduleName(":app", "main")).dependencies)
    }
}
