package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ModuleDependencyPlanTest {
    private fun multiplatformLibrary(): ModuleDsl.() -> Unit = {
        target("jvm", Platform.JVM)
        target("ios", Platform.NATIVE)
        sourceSet("commonMain", targets = listOf("jvm", "ios"))
        sourceSet("jvmMain", dependsOn = listOf("commonMain"), targets = listOf("jvm"))
        sourceSet("iosMain", dependsOn = listOf("commonMain"), targets = listOf("ios"))
        sourceSet("commonTest", targets = listOf("jvm", "ios"))
        sourceSet("jvmTest", dependsOn = listOf("commonTest"), targets = listOf("jvm"))
    }

    @Test
    fun `an unscoped dependency applies to every source set of the module`() {
        val plan = model {
            module(":lib", multiplatformLibrary())
            module(":app", multiplatformLibrary())
            dependency(from = ":app", to = ":lib")
        }.planAll()
        val libCommon = moduleName(":lib", "commonMain")
        assertEquals(listOf(libCommon), plan.module(moduleName(":app", "commonMain")).dependencies)
        assertEquals(
            listOf(libCommon, moduleName(":lib", "jvmMain")),
            plan.module(moduleName(":app", "jvmMain")).dependencies,
        )
        assertEquals(
            listOf(libCommon, moduleName(":lib", "iosMain")),
            plan.module(moduleName(":app", "iosMain")).dependencies,
        )
    }

    @Test
    fun `a dependency scoped to a source set reaches the source sets that refine it`() {
        val plan = model {
            module(":lib", multiplatformLibrary())
            module(":app", multiplatformLibrary())
            dependency(from = ":app", to = ":lib", scope = "commonMain")
        }.planAll()
        val libCommon = moduleName(":lib", "commonMain")
        assertEquals(listOf(libCommon), plan.module(moduleName(":app", "commonMain")).dependencies)
        assertEquals(
            listOf(libCommon, moduleName(":lib", "jvmMain")),
            plan.module(moduleName(":app", "jvmMain")).dependencies,
        )
        val commonTest = plan.module(moduleName(":app", "commonTest"))
        assertEquals(listOf(moduleName(":app", "commonMain"), libCommon), commonTest.dependencies)
    }

    @Test
    fun `a dependency scoped to one platform does not reach the others`() {
        val plan = model {
            module(":lib", multiplatformLibrary())
            module(":app", multiplatformLibrary())
            dependency(from = ":app", to = ":lib", scope = "jvmMain")
        }.planAll()
        assertEquals(emptyList(), plan.module(moduleName(":app", "commonMain")).dependencies)
        assertEquals(emptyList(), plan.module(moduleName(":app", "iosMain")).dependencies)
        assertEquals(2, plan.module(moduleName(":app", "jvmMain")).dependencies.size)
    }

    @Test
    fun `test source sets inherit the dependencies of their friends and add their own`() {
        val plan = model {
            module(":lib", multiplatformLibrary())
            module(":testing") {
                target("jvm", Platform.JVM)
                sourceSet("main", targets = listOf("jvm"))
            }
            module(":app") {
                target("jvm", Platform.JVM)
                sourceSet("main", targets = listOf("jvm"))
                sourceSet("test", targets = listOf("jvm"))
            }
            dependency(from = ":app", to = ":lib")
            dependency(from = ":app", to = ":testing", scope = "test", configuration = "testImplementation")
        }.planAll()
        val main = plan.module(moduleName(":app", "main"))
        val test = plan.module(moduleName(":app", "test"))
        assertEquals(listOf(moduleName(":lib", "commonMain"), moduleName(":lib", "jvmMain")), main.dependencies)
        assertEquals(
            listOf(
                moduleName(":app", "main"),
                moduleName(":lib", "commonMain"),
                moduleName(":lib", "jvmMain"),
                moduleName(":testing", "main"),
            ),
            test.dependencies,
        )
        assertEquals(listOf(moduleName(":app", "main")), test.friends)
    }

    @Test
    fun `a dependency is expanded with everything its target refines`() {
        val plan = model {
            module(":lib") {
                target("jvm", Platform.JVM)
                target("android", Platform.ANDROID)
                sourceSet("commonMain", targets = listOf("jvm", "android"))
                sourceSet("jvmAndAndroidMain", dependsOn = listOf("commonMain"), targets = listOf("jvm", "android"))
                sourceSet("jvmMain", dependsOn = listOf("jvmAndAndroidMain"), targets = listOf("jvm"))
            }
            module(":app") {
                target("jvm", Platform.JVM)
                sourceSet("main", targets = listOf("jvm"))
            }
            dependency(from = ":app", to = ":lib")
        }.planAll()
        val expected = listOf("commonMain", "jvmAndAndroidMain", "jvmMain").map { moduleName(":lib", it) }
        assertEquals(expected, plan.module(moduleName(":app", "main")).dependencies)
    }

    @Test
    fun `common code depends on the common part of a module and a jvm module falls back to main`() {
        val plan = model {
            module(":plain") {
                target("jvm", Platform.JVM)
                sourceSet("main", targets = listOf("jvm"))
            }
            module(":lib", multiplatformLibrary())
            module(":shared") {
                target("jvm", Platform.JVM)
                target("ios", Platform.NATIVE)
                sourceSet("commonMain", targets = listOf("jvm", "ios"))
            }
            module(":android") {
                target("android", Platform.ANDROID)
                sourceSet("main", targets = listOf("android"))
            }
            dependency(from = ":shared", to = ":plain")
            dependency(from = ":shared", to = ":lib")
            dependency(from = ":android", to = ":plain")
        }.planAll()
        val shared = plan.module(moduleName(":shared", "commonMain"))
        assertEquals(listOf(moduleName(":lib", "commonMain"), moduleName(":plain", "main")), shared.dependencies)
        assertEquals(listOf(moduleName(":plain", "main")), plan.module(moduleName(":android", "main")).dependencies)
    }

    @Test
    fun `the api dependencies of a dependency are visible to its consumers but its implementation ones are not`() {
        val plan = model {
            module(":exposed") { sourceSet("main") }
            module(":hidden") { sourceSet("main") }
            module(":middle") { sourceSet("main") }
            module(":app") { sourceSet("main") }
            dependency(from = ":middle", to = ":exposed", configuration = "api")
            dependency(from = ":middle", to = ":hidden", configuration = "implementation")
            dependency(from = ":app", to = ":middle")
        }.planAll()
        val expected = listOf(":exposed", ":middle").map { moduleName(it, "main") }
        assertEquals(expected, plan.module(moduleName(":app", "main")).dependencies)
    }

    @Test
    fun `external dependencies are ignored and edges may start at a source set`() {
        val plan = model {
            module(":lib") { sourceSet("main") }
            module(":app") { sourceSet("main") }
            external(from = ":app", coordinate = "maven:org.example:thing")
            dependencyFromSourceSet(from = ":app", sourceSet = "main", to = ":lib")
        }.planAll()
        assertEquals(listOf(moduleName(":lib", "main")), plan.module(moduleName(":app", "main")).dependencies)
        assertFalse(plan.dependenciesAssumed)
    }
}
