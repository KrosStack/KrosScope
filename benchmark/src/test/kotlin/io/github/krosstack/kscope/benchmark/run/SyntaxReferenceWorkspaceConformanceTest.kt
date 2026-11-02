package io.github.krosstack.kscope.benchmark.run

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Scans the reference workspace at level syntax: declarations and the build model hold, nothing is resolved. */
class SyntaxReferenceWorkspaceConformanceTest {
    private val result = ConformanceFixture.syntax

    @Test
    fun `the scan has no errors and the result says which level it stands for`() {
        assertEquals(0, result.scan.errors, "${result.coverage.diagnosticsByCode}")
        assertEquals("syntax", result.level)
        assertEquals(emptyList(), result.goldProblems)
    }

    @Test
    fun `every declaration of the closed-world files is found and nothing else`() {
        val declarations = result.declarations.overall
        assertTrue(declarations.precision >= GateThresholds.DECLARATION_PRECISION, "${declarations.extras}")
        assertTrue(declarations.recall >= GateThresholds.DECLARATION_RECALL, "${declarations.misses}")
    }

    @Test
    fun `the edges that exist meet the precision threshold`() {
        val edges = result.closedWorld.overall(result.gateSelection())
        assertTrue(edges.precision >= GateThresholds.EDGE_PRECISION, "${edges.extras}")
    }

    @Test
    fun `no relationship has compiler evidence`() {
        assertEquals(emptyList(), result.coverage.rows.filter { it.evidence == "compiler" })
    }

    @Test
    fun `source sets say that references were not analysed`() {
        assertTrue((result.coverage.diagnosticsByCode[NOT_ANALYSED_AT_SYNTAX] ?: 0) > 0)
    }

    @Test
    fun `no resolved workspace target is without a symbol`() {
        assertEquals(0, result.coverage.integrity.dangling, "${result.coverage.integrity.examples}")
    }

    @Test
    fun `the build model read statically equals gold`() {
        val differences = result.buildModel.dimensions.flatMap { it.score.misses + it.score.extras }
        assertEquals(emptyList(), differences)
    }

    @Test
    fun `every relationship class that exists is precise and the recall of the others cannot drop`() {
        val classes = result.relationshipClasses.associateBy { it.name }
        FLOORS.forEach { (name, floor) ->
            val score = checkNotNull(classes.getValue(name).score)
            assertTrue(score.recall >= floor, "$name recall ${score.recall} is below $floor ${score.misses}")
        }
        classes.values.mapNotNull { it.score }.forEach {
            assertTrue(it.precision >= GateThresholds.EDGE_PRECISION, "${it.extras}")
        }
    }

    @Test
    fun `the gate of syntax level is met`() {
        assertEquals(emptyList(), result.checkGate(result.gateSelection()).failures)
    }

    private companion object {
        val FLOORS = mapOf(
            "expect_actual" to 1.0,
            "catalog_alias_to_module" to 1.0,
            "convention_plugin_to_module" to 1.0,
            "manifest_component_to_class" to 1.0,
            "layout_class_reference" to 1.0,
            "aidl_parcelable_to_class" to 1.0,
            "jni_external_to_native" to 1.0,
            "test_to_subject" to 0.88,
            "resource_to_consumer" to 0.61,
            "room_entity_dao_database" to 0.25,
        )
    }
}
