package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.SyntheticSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AndroidGeneratedSourcesTest {
    private val model = model {
        module(":app") {
            target("android", Platform.ANDROID)
            variant("premiumDebug", "debug", "premium")
            sourceSet("main", targets = listOf("android"), variants = listOf("premiumDebug"))
            sourceSet("premium", targets = listOf("android"), variants = listOf("premiumDebug"))
            sourceSet("debug", targets = listOf("android"), variants = listOf("premiumDebug"))
            sourceSet("premiumDebug", targets = listOf("android"), variants = listOf("premiumDebug"))
        }
    }
    private val dimensions = model {
        module(":app") {
            target("android", Platform.ANDROID)
            variant("zetaAlphaDebug", "debug", "zeta", "alpha")
            sourceSet("main", targets = listOf("android"), variants = listOf("zetaAlphaDebug"))
            sourceSet("zeta", targets = listOf("android"), variants = listOf("zetaAlphaDebug"))
            sourceSet("alpha", targets = listOf("android"), variants = listOf("zetaAlphaDebug"))
            sourceSet("zetaAlpha", targets = listOf("android"), variants = listOf("zetaAlphaDebug"))
        }
    }

    @Test
    fun `a flavor binding overrides main in the default compilation group`() {
        assertEquals(listOf("premium"), ownersOf("main", "premium"))
    }

    @Test
    fun `a build type binding overrides a flavor in the default compilation group`() {
        assertEquals(listOf("debug"), ownersOf("premium", "debug"))
    }

    @Test
    fun `a variant binding overrides its build type in the default compilation group`() {
        assertEquals(listOf("premiumDebug"), ownersOf("debug", "premiumDebug"))
    }

    @Test
    fun `flavors follow dimension priority rather than alphabetical order`() {
        assertEquals(listOf("zeta"), ownersOf("alpha", "zeta", build = dimensions))
    }

    @Test
    fun `a combined flavor binding overrides its individual flavors`() {
        assertEquals(listOf("zetaAlpha"), ownersOf("alpha", "zeta", "zetaAlpha", build = dimensions))
    }

    @Test
    fun `shared sources are context without emitting declarations again in another flavor`() {
        val full = flavorPlan().module(moduleName(":app", "tan"))
        val main = full.sourceSets.single { it.sourceSet == sourceSetId(":app", "main") }
        assertFalse(main.emitFactsFor)
        assertEquals(
            listOf(sourceSetId(":app", "tan")),
            full.sourceSets.filter { it.emitFactsFor }.map { it.sourceSet },
        )
    }

    @Test
    fun `a flavor context retains shared dependencies and classpaths`() {
        val full = flavorPlan().module(moduleName(":app", "tan"))
        assertEquals(listOf(moduleName(":lib", "main")), full.dependencies)
        assertEquals(listOf("lib.jar"), full.classpath)
    }

    @Test
    fun `empty context sources do not take ownership of the dependency assumption notice`() {
        val build = model {
            module(":app") {
                target("android", Platform.ANDROID)
                variant("redDebug", "debug", "red")
                variant("tanDebug", "debug", "tan")
                sourceSet("main", targets = listOf("android"), variants = listOf("redDebug", "tanDebug"))
                sourceSet("red", targets = listOf("android"), variants = listOf("redDebug"))
                sourceSet("tan", targets = listOf("android"), variants = listOf("tanDebug"))
            }
        }
        val owners = listOf("red", "tan").map { sourceSetId(":app", it) }
        val stubs = owners.map {
            SyntheticSource("Welcome.java", "java", "class Welcome {}", it, "android.viewbinding")
        }
        val plan = build.plan(owners, without = setOf(sourceSetId(":app", "main")), synthetic = stubs)
        assertEquals(sourceSetId(":app", "red"), plan.assumptionAnnouncer)
    }

    private fun flavorPlan(): AnalysisPlan {
        val flavors = model {
            module(":app") {
                target("android", Platform.ANDROID)
                variant("redDebug", "debug", "red")
                variant("tanDebug", "debug", "tan")
                sourceSet("main", targets = listOf("android"), variants = listOf("redDebug", "tanDebug"))
                sourceSet("red", targets = listOf("android"), variants = listOf("redDebug"))
                sourceSet("tan", targets = listOf("android"), variants = listOf("tanDebug"))
            }
            module(":lib") {
                target("jvm", Platform.JVM)
                sourceSet("main", targets = listOf("jvm"))
            }
            dependency(":app", ":lib", scope = "main")
        }
        val stubs = listOf("red", "tan").map { name ->
            SyntheticSource(
                "Welcome.java",
                "java",
                "class Welcome {}",
                sourceSetId(":app", name),
                "android.viewbinding",
            )
        }
        return flavors.plan(
            flavors.sourceSets.map { it.id },
            synthetic = stubs,
            classpaths = mapOf(sourceSetId(":app", "main") to listOf("lib.jar")),
        )
    }

    private fun ownersOf(vararg names: String, build: BuildModel = model): List<String> {
        val stubs = names.map { name ->
            SyntheticSource(
                "android-viewbinding/sample/databinding/WelcomeBinding.java",
                "java",
                "package sample.databinding; public class WelcomeBinding {}",
                sourceSetId(":app", name),
                "android.viewbinding",
            )
        }
        val plan = build.plan(build.sourceSets.map { it.id }, synthetic = stubs)
        val variant = build.variants.single().name
        return plan.module(groupName(":app", variant)).sourceSets.flatMap { it.syntheticSources }
            .map { it.sourceSet.value.substringAfterLast('@') }
    }
}
