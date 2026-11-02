package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.analyzer.gradle.statics.edgesOf
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassPluginDependenciesStaticTest {
    private val tree = ClassConventionTree()
    private val provided = tree.provided

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    @Test
    fun `dependencies of a class are read from project, add, catalog lookups and bundles`() {
        val expected = listOf(
            ":core" to "implementation",
            "maven:androidx.tracing:tracing-ktx" to "implementation",
            "maven:com.android.tools:desugar_jdk_libs" to "coreLibraryDesugaring",
            "maven:junit:junit" to "testImplementation",
            "maven:junit:junit" to "testImplementation",
            "maven:org.jetbrains.kotlin:kotlin-test" to "testImplementation",
        )
        assertEquals(expected, tree.dependenciesOf(":lib"))
    }

    @Test
    fun `dependencies that a class declares are heuristic and located in the class`() {
        val junit = provided.edgesOf(":lib").first { it.attributes["version"] == "4.13.2" }
        assertEquals(Certainty.HEURISTIC, (junit.resolution as Resolution.Resolved).certainty)
        assertEquals("${ClassConventionTree.LOGIC}/AndroidLibraryPlugin.kt", junit.site?.artifact?.path?.value)
        val own = provided.edgesOf(":lib").single { (it.resolution as Resolution.Resolved).target is NodeRef.OfModule }
        assertEquals(Certainty.EXACT, (own.resolution as Resolution.Resolved).certainty)
    }

    @Test
    fun `a catalog entry that a class reads by alias is referenced from the class`() {
        val site = "${ClassConventionTree.LOGIC}/AndroidLibraryPlugin.kt"
        val references = provided.facts.filterIsInstance<Fact.OfRelationship>().map { it.value }
            .filter { it.site?.artifact?.path?.value == site }
        val aliases = references.map { it.attributes["alias"] }.toSet()
        assertEquals(setOf("junit", "androidx.tracing.ktx", "testing", "desugar.jdk.libs"), aliases)
        assertTrue(references.all { (it.resolution as Resolution.Resolved).certainty == Certainty.HEURISTIC })
    }

    @Test
    fun `a bill of materials read into a local keeps its coordinate`() {
        val coordinates = tree.dependenciesOf(":ui").map { it.first }
        assertTrue("maven:androidx.compose:compose-bom" in coordinates)
        assertTrue("maven:androidx.compose.ui:ui" in coordinates)
    }

    @Test
    fun `project dependencies of an applied class and of the class it applies are kept`() {
        val projects = tree.dependenciesOf(":feature").filter { it.first.startsWith(":") }
        assertEquals(listOf(":core" to "api", ":ui" to "implementation"), projects)
    }

    @Test
    fun `add with a project and a coordinate are read`() {
        assertEquals(
            listOf(":core" to "implementation", "maven:org.example:library" to "implementation"),
            tree.dependenciesOf(":aggregate"),
        )
        val library = provided.edgesOf(":aggregate").single { it.attributes["version"] == "1.0" }
        assertEquals(Certainty.HEURISTIC, (library.resolution as Resolution.Resolved).certainty)
    }
}
