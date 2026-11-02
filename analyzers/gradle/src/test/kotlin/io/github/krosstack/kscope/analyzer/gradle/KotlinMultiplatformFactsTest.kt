package io.github.krosstack.kscope.analyzer.gradle

import io.github.krosstack.kscope.analyzer.gradle.mapping.SampleModel
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinMultiplatformFactsTest {
    private val sample = SampleModel.load("kmp-android-library.json")
    private val sdk = "android.compileSdk" to "36"

    @Test
    fun `source roots are repository relative and typed by their directory`() {
        val roots = sample.sourceSets(":core:model").getValue("commonMain").roots
        assertEquals(listOf("core/model/src/commonMain/kotlin"), roots.map { it.directory.value })
        assertEquals("kotlin", roots.single().kind.value)
        assertTrue(roots.none { it.generated })
    }

    @Test
    fun `generator output below the build directory is a generated root`() {
        val generated = sample.sourceSets(":data:db").getValue("commonMain").roots.single { it.generated }
        assertEquals("data/db/build/generated/sqldelight/code/TaskDatabase/commonMain", generated.directory.value)
        assertEquals("kotlin", generated.kind.value)
    }

    @Test
    fun `a source set that only has generated roots is still modelled`() {
        val androidMain = sample.sourceSets(":ui:shared").getValue("androidMain")
        assertTrue(androidMain.roots.all { it.generated })
        assertEquals(listOf("tasktide//:ui:shared@commonMain"), androidMain.dependsOn.map { it.value })
    }

    @Test
    fun `plugin and generator facts become sorted module attributes`() {
        assertEquals(mapOf(sdk, "kotlin.plugin.serialization" to "true"), sample.module(":core:model").attributes)
        val database = "sqldelight.database.TaskDatabase.package" to "sample.tasktide.data.db"
        assertEquals(mapOf(sdk, database), sample.module(":data:db").attributes)
        assertEquals(mapOf(sdk, "kotlin.plugin.compose" to "true"), sample.module(":ui:shared").attributes)
    }

    @Test
    fun `the classpath of a shared source set is the union over its platforms`() {
        val common = sample.classpath(":core:model@commonMain")
        val android = sample.classpath(":core:model@androidMain")
        val desktop = sample.classpath(":core:model@desktopMain")
        assertTrue(common.containsAll(android) && common.containsAll(desktop))
        assertEquals(common, common.distinct())
        assertTrue(android.any { it.endsWith("android.jar") })
    }

    @Test
    fun `a dependency declared for one source set is scoped to it`() {
        val moduleEdge = sample.dependsOn(":data:db").single {
            val target = (it.resolution as? Resolution.Resolved)?.target
            it.attributes["configuration"] == "commonMainApi" && target is NodeRef.OfModule
        }
        assertEquals("tasktide//:data:db@commonMain", moduleEdge.scope.sourceSet?.value)
        assertEquals(listOf(":core:model" to "commonMainApi"), sample.moduleDependencies(":data:db"))
    }

    @Test
    fun `external dependencies carry their coordinate, version and configuration`() {
        val edge = sample.dependsOn(":core:model").first { it.resolution is Resolution.Resolved && isExternal(it) }
        assertTrue(edge.attributes.getValue("configuration").isNotEmpty())
        assertTrue(edge.attributes.getValue("version").isNotEmpty())
        assertTrue(sample.externalDependencies(":core:model").all { it.coordinate.startsWith("maven:") })
    }

    private fun isExternal(edge: Relationship) = (edge.resolution as Resolution.Resolved).target is NodeRef.OfExternal
}
