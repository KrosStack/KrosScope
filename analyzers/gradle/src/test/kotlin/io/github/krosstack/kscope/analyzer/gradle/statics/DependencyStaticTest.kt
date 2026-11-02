package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DependencyStaticTest {
    private val kotlinTree = StaticWorkspace("dependencies-kotlin")
    private val groovyTree = StaticWorkspace("dependencies-groovy")
    private val kotlin = kotlinTree.provided()
    private val groovy = groovyTree.provided()

    @AfterTest
    fun cleanUp() {
        kotlinTree.close()
        groovyTree.close()
    }

    private fun Relationship.target(): String {
        val resolution = resolution
        if (resolution !is Resolution.Resolved) return "unresolved:" + (resolution as Resolution.Unresolved).hint
        return when (val target = resolution.target) {
            is NodeRef.OfExternal -> target.coordinate
            is NodeRef.OfModule -> target.id.value
            else -> error("Unexpected target $target")
        }
    }

    private fun summary(edges: List<Relationship>) =
        edges.sortedWith(compareBy({ it.site?.range?.startLine }, { it.target() }))
            .map { Triple(it.attributes["configuration"], it.target(), it.attributes["version"]) }

    @Test
    fun `every dependency form of a module becomes an edge with its configuration and version`() {
        assertEquals(
            listOf(
                Triple("implementation", "tasktide//:core:model", null),
                Triple("api", "tasktide//:core:model-api", null),
                Triple("implementation", "maven:org.jetbrains.kotlinx:kotlinx-coroutines-core", "1.11.0"),
                Triple("implementation", "maven:androidx.compose:compose-bom", "2026.06.01"),
                Triple("implementation", "maven:org.slf4j:slf4j-api", "2.0.17"),
                Triple("runtimeOnly", "maven:org.slf4j:slf4j-simple", null),
                Triple("testImplementation", "maven:org.jetbrains.kotlin:kotlin-test", null),
                Triple("compileOnly", "maven:javax.inject:javax.inject", "1"),
                Triple("annotationProcessor", "maven:com.google.dagger:dagger-compiler", "2.51"),
                Triple("implementation", "file:app/libs/local.jar", null),
                Triple("implementation", "unresolved::missing", null),
                Triple("testImplementation", "maven:junit:junit", "4.13.2"),
                Triple("testImplementation", "maven:org.jetbrains.kotlin:kotlin-test", "2.4.20"),
                Triple("ksp", "maven:androidx.room:room-compiler", "2.8.5"),
            ),
            summary(kotlin.edgesOf(":app")),
        )
    }

    @Test
    fun `project dependencies are module edges and a missing project is unresolved`() {
        val edges = kotlin.edgesOf(":app")
        val model = edges.first { it.target() == "tasktide//:core:model" }
        val target = NodeRef.OfModule(kotlin.module(":core:model").id)
        assertEquals(Resolution.Resolved(target, Certainty.EXACT), model.resolution)
        assertEquals(RelationKind.DEPENDS_ON, model.kind)
        val missing = edges.single { it.target() == "unresolved::missing" }
        assertEquals("gradle.static.project-not-found", (missing.resolution as Resolution.Unresolved).reason.value)
    }

    @Test
    fun `type safe project accessors turn camel case into the project path`() {
        assertTrue(kotlin.edgesOf(":app").any { it.target() == "tasktide//:core:model-api" })
    }

    @Test
    fun `a configuration of a source set is scoped to it when the source set exists`() {
        val edges = kotlin.edgesOf(":app").associateBy { it.attributes["configuration"] to it.target() }
        assertEquals(
            "tasktide//:app@test",
            edges.getValue("testImplementation" to "maven:org.jetbrains.kotlin:kotlin-test").scope.sourceSet?.value,
        )
        assertEquals(null, edges.getValue("implementation" to "tasktide//:core:model").scope.sourceSet)
    }

    @Test
    fun `dependencies in source set blocks of a multiplatform module are scoped to the source set`() {
        val edges = kotlin.edgesOf(":data")
        assertEquals(
            listOf(
                Triple("commonMainApi", "tasktide//:core:model", null),
                Triple("commonMainImplementation", "maven:org.jetbrains.kotlinx:kotlinx-coroutines-core", "1.11.0"),
                Triple("jvmMainImplementation", "maven:org.xerial:sqlite-jdbc", "3.53.4.0"),
                Triple("jvmTestImplementation", "maven:org.jetbrains.kotlin:kotlin-test", null),
            ),
            summary(edges.sortedBy { it.attributes["configuration"] }),
        )
        assertEquals(
            listOf("commonMain", "commonMain", "jvmMain", "jvmTest"),
            edges.sortedBy { it.attributes["configuration"] }.map { it.scope.sourceSet?.value?.substringAfter('@') },
        )
    }

    @Test
    fun `a notation that cannot be resolved is an information and a file keeps its path as written`() {
        val info = kotlin.problems("gradle.static.dependency-not-analysed").single()
        assertEquals(DiagnosticSeverity.INFO, info.severity)
        assertTrue("compose.desktop.currentOs" in info.message)
        val file = kotlin.edgesOf(":app").single { it.target() == "file:app/libs/local.jar" }
        assertEquals("libs/local.jar", file.attributes["path"])
    }

    @Test
    fun `groovy dependency forms give the same kinds of edges`() {
        assertEquals(
            listOf(
                Triple("implementation", "tasktide//:core", null),
                Triple("api", "maven:com.google.guava:guava", "33.0.0-jre"),
                Triple("implementation", "maven:org.slf4j:slf4j-api", "2.0.17"),
                Triple("testImplementation", "maven:junit:junit", "4.13.2"),
                Triple("testImplementation", "maven:junit:junit", "4.13.2"),
                Triple("testImplementation", "maven:org.jetbrains.kotlin:kotlin-test", "2.4.20"),
                Triple("compileOnly", "maven:org.jetbrains.kotlin:kotlin-test", "2.4.20"),
                Triple("implementation", "file:app/libs/local.jar", null),
            ),
            summary(groovy.edgesOf(":app")),
        )
    }
}
