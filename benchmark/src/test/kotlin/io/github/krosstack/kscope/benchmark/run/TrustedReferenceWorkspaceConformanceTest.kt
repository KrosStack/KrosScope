package io.github.krosstack.kscope.benchmark.run

import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scans the reference workspace with its Gradle build trusted, so the analyzer sees the exact build model and the
 * compile classpaths, and checks the gate for all closed-world files. Enabled with `-Pkscope.integration=true`.
 */
@Tag("gradle-integration")
class TrustedReferenceWorkspaceConformanceTest {
    private val result = ConformanceFixture.trusted

    @Test
    fun `the scan has no errors and the gold data is consistent with the workspace`() {
        assertEquals(0, result.scan.errors, "${result.coverage.diagnosticsByCode}")
        assertEquals(emptyList(), result.goldProblems)
    }

    @Test
    fun `every declaration is found and nothing else`() {
        val declarations = result.declarations.overall
        assertTrue(declarations.precision >= GateThresholds.DECLARATION_PRECISION, "${declarations.extras}")
        assertTrue(declarations.recall >= GateThresholds.DECLARATION_RECALL, "${declarations.misses}")
    }

    @Test
    fun `edges of all closed-world files meet the thresholds including those that need the android classpath`() {
        val edges = result.closedWorld.overall(FileSelection.ALL)
        assertTrue(edges.precision >= GateThresholds.EDGE_PRECISION, "${edges.extras}")
        assertTrue(edges.recall >= GateThresholds.EDGE_RECALL, "${edges.misses}")
        val android = result.closedWorld.files.filter { it.androidDependent }
        assertTrue(android.isNotEmpty() && android.all { it.score.recall >= GateThresholds.EDGE_RECALL })
    }

    @Test
    fun `no resolved workspace target is without a symbol`() {
        assertEquals(0, result.coverage.integrity.dangling, "${result.coverage.integrity.examples}")
    }

    @Test
    fun `the build model that Gradle reports equals gold and the build logic is the included build`() {
        val dimensions = result.buildModel.dimensions.associate { it.name to it.score }
        assertEquals(emptyList(), dimensions.values.flatMap { it.misses + it.extras })
        assertEquals(1, dimensions.getValue("included builds").expected)
    }

    @Test
    fun `the catalog symbols and the references to them do not depend on who provided the build model`() {
        val catalog = result.relationshipClasses.single { it.name == "catalog_alias_to_module" }
        assertEquals(emptyList(), catalog.score?.let { it.misses + it.extras })
        assertTrue(result.coverage.symbols >= CATALOG_AND_SOURCE_SYMBOLS)
    }

    @Test
    fun `every supported relationship class is found completely`() {
        val supported = result.relationshipClasses.filter { it.supported }
        assertTrue(supported.size >= 10, "${supported.map { it.name }}")
        supported.filter { it.name != RESOURCES && it.name !in SchemaConformance.CLASSES }.forEach { entry ->
            assertEquals(emptyList(), entry.score?.let { it.misses + it.extras }, entry.name)
            assertEquals(0, entry.evidenceMismatches, entry.name)
        }
    }

    @Test
    fun `resources are found in xml, in kotlin and java, in the manifest and through view binding`() {
        val resources = result.relationshipClasses.single { it.name == RESOURCES }
        val parts = resources.parts.associateBy { it.label }
        assertEquals(emptyList(), resources.score?.extras)
        assertEquals(1.0, parts.getValue("XML").score.recall, "${parts.getValue("XML").score.misses}")
        assertEquals(1.0, parts.getValue("Kotlin/Java").score.recall, "${parts.getValue("Kotlin/Java").score.misses}")
        assertEquals(1.0, parts.getValue("Manifest").score.recall, "${parts.getValue("Manifest").score.misses}")
        assertEquals(1.0, parts.getValue("Kotlin layout (view binding)").score.recall)
    }

    @Test
    fun `the gate of trusted mode is met`() {
        assertEquals(emptyList(), result.checkGate(FileSelection.ALL).failures)
    }

    private companion object {
        const val CATALOG_AND_SOURCE_SYMBOLS = 595
        const val RESOURCES = "resource_to_consumer"
    }
}
