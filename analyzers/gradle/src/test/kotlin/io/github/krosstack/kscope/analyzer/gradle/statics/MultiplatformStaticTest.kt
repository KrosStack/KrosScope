package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiplatformStaticTest {
    private val tree = StaticWorkspace("kmp-agp")
    private val agp = tree.provided()

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    @Test
    fun `the android multiplatform library plugin makes one android target and no variants`() {
        val shared = agp.module(":shared")
        assertEquals(setOf(ModuleTrait.ANDROID_LIBRARY, ModuleTrait.KOTLIN_MULTIPLATFORM), shared.traits)
        assertEquals("sample.shared", shared.androidNamespace)
        assertEquals("36", shared.attributes["android.compileSdk"])
        assertEquals(
            listOf("android" to Platform.ANDROID, "desktop" to Platform.JVM),
            agp.model.targets.filter { it.module.value == "tasktide//:shared" }.map { it.name to it.platform },
        )
        assertTrue(agp.model.variants.isEmpty())
    }

    @Test
    fun `main source sets always exist and test source sets exist when they have a directory`() {
        assertEquals(
            listOf(
                "androidInstrumentedTest",
                "androidMain",
                "commonMain",
                "commonTest",
                "desktopMain",
                "desktopTest",
                "shared",
            ),
            agp.sourceSets(":shared").keys.toList(),
        )
    }

    @Test
    fun `source sets depend on the common ones, and explicit dependsOn edges are added`() {
        val edges = agp.dependsOnOf(":shared")
        assertEquals(emptyList(), edges.getValue("commonMain"))
        assertEquals(listOf("commonMain"), edges.getValue("androidMain"))
        assertEquals(listOf("commonMain", "shared"), edges.getValue("desktopMain"))
        assertEquals(listOf("commonMain"), edges.getValue("shared"))
        assertEquals(listOf("commonTest"), edges.getValue("desktopTest"))
        assertEquals(listOf("commonTest"), edges.getValue("androidInstrumentedTest"))
    }

    @Test
    fun `declared source sets are exact and the template ones are heuristic`() {
        val sourceSets = agp.sourceSets(":shared")
        assertEquals(Certainty.EXACT, sourceSets.getValue("shared").certainty)
        assertEquals(Certainty.HEURISTIC, sourceSets.getValue("desktopMain").certainty)
    }

    @Test
    fun `a source set compiles for the targets that share it`() {
        val sourceSets = agp.sourceSets(":shared")
        assertEquals(2, sourceSets.getValue("commonMain").targets.size)
        val android = sourceSets.getValue("androidMain")
        assertEquals(listOf("tasktide//:shared#target/android"), android.targets.map { it.value })
        val desktop = sourceSets.getValue("desktopTest")
        assertEquals(listOf("tasktide//:shared#target/desktop"), desktop.targets.map { it.value })
    }

    @Test
    fun `directories that the build model does not expose are roots of the common source set`() {
        val roots = agp.sourceSets(":shared").getValue("commonMain").roots.map { it.directory.value to it.kind.value }
        assertEquals(
            listOf(
                "shared/src/commonMain/composeResources" to "compose.resources",
                "shared/src/commonMain/kotlin" to "kotlin",
                "shared/src/commonMain/sqldelight" to "sqldelight",
            ),
            roots,
        )
    }

    @Test
    fun `dependencies of source set blocks are scoped to their source set`() {
        val edges = agp.edgesOf(":shared")
        val common = edges.filter { it.attributes["configuration"]?.startsWith("commonMain") == true }
        val configurations = common.map { it.attributes.getValue("configuration") }.sorted()
        assertEquals(listOf("commonMainApi", "commonMainImplementation"), configurations)
        assertEquals(setOf("tasktide//:shared@commonMain"), common.map { it.scope.sourceSet?.value }.toSet())
        val project = common.first { it.attributes["configuration"] == "commonMainApi" }
        val data = NodeRef.OfModule(agp.module(":data").id)
        assertEquals(Resolution.Resolved(data, Certainty.EXACT), project.resolution)
        val desktop = edges.single { it.attributes["configuration"] == "desktopMainImplementation" }
        assertEquals("tasktide//:shared@desktopMain", desktop.scope.sourceSet?.value)
        assertEquals("3.53.4.0", desktop.attributes["version"])
    }
}
