package io.github.krosstack.kscope.benchmark.run

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Scans the reference workspace without trusting its build and checks the Kotlin analyzer gate for default mode. */
class ReferenceWorkspaceConformanceTest {
    private val result = ConformanceFixture.default

    @Test
    fun `the scan of the reference workspace has no errors and the gold data is consistent with it`() {
        assertEquals(0, result.scan.errors, "${result.coverage.diagnosticsByCode}")
        assertEquals(emptyList(), result.goldProblems)
    }

    @Test
    fun `every declaration of the closed-world files is found and nothing else`() {
        val declarations = result.declarations.overall
        assertTrue(declarations.precision >= GateThresholds.DECLARATION_PRECISION, "${declarations.extras}")
        assertTrue(declarations.recall >= GateThresholds.DECLARATION_RECALL, "${declarations.misses}")
    }

    @Test
    fun `edges of the files that need no android classpath meet the thresholds`() {
        val edges = result.closedWorld.overall(FileSelection.WITHOUT_ANDROID)
        assertTrue(edges.precision >= GateThresholds.EDGE_PRECISION, "${edges.extras}")
        assertTrue(edges.recall >= GateThresholds.EDGE_RECALL, "${edges.misses}")
    }

    @Test
    fun `no resolved workspace target is without a symbol`() {
        assertEquals(0, result.coverage.integrity.dangling, "${result.coverage.integrity.examples}")
        assertTrue(result.coverage.integrity.checked > 0)
    }

    @Test
    fun `the build model read statically equals gold`() {
        val differences = result.buildModel.dimensions.flatMap { it.score.misses + it.score.extras }
        assertEquals(emptyList(), differences)
    }

    @Test
    fun `the relationship classes of this phase are found and the misses are the known ones`() {
        val supported = result.relationshipClasses.filter { it.supported }.associateBy { it.name }
        assertTrue(supported.keys.containsAll(SUPPORTED_CLASSES), "${supported.keys}")
        val classes = supported.filterKeys { it != RESOURCES && it !in SchemaConformance.CLASSES }.values
        val misses = classes.flatMap { it.score?.misses.orEmpty() }.map { it.substringBefore(' ') }
        assertTrue(KNOWN_ANDROID_MISSES.containsAll(misses), "$misses")
        assertEquals(emptyList(), classes.flatMap { it.score?.extras.orEmpty() })
    }

    @Test
    fun `every relationship class of the gold data is scored`() {
        val unsupported = result.relationshipClasses.filterNot { it.supported }
        assertEquals(emptyList(), unsupported.map { it.name })
        assertEquals(SUPPORTED_CLASSES, result.relationshipClasses.map { it.name }.toSet())
    }

    @Test
    fun `the gate of default mode is met`() {
        assertEquals(emptyList(), result.checkGate(FileSelection.WITHOUT_ANDROID).failures)
    }

    private companion object {
        val SUPPORTED_CLASSES = setOf(
            "expect_actual",
            "kotlin_calls_java",
            "java_calls_kotlin",
            "catalog_alias_to_module",
            "convention_plugin_to_module",
            "manifest_component_to_class",
            "layout_class_reference",
            "viewbinding_layout_to_consumer",
            "buildconfig_field_to_consumer",
            "aidl_interface_to_implementer",
            "aidl_interface_to_client",
            "aidl_method_to_override",
            "aidl_parcelable_to_class",
            "jni_external_to_native",
            "compose_resource_to_consumer",
            "flavor_specific_implementation",
            "test_to_subject",
            RESOURCES,
        ) + SchemaConformance.CLASSES

        val KNOWN_ANDROID_MISSES = setOf("kotlin_calls_java:016")
        const val RESOURCES = "resource_to_consumer"
    }
}
