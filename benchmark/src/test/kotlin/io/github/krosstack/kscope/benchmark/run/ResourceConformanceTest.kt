package io.github.krosstack.kscope.benchmark.run

import kotlin.test.Test
import kotlin.test.assertEquals

/** What the resource producer, the `R` stub and the resource linker achieve on the reference workspace. */
class ResourceConformanceTest {
    private val result = ConformanceFixture.default

    @Test
    fun `resources reach the xml and the code that refers to them and nothing is made up`() {
        val resources = result.relationshipClasses.single { it.name == "resource_to_consumer" }
        val parts = resources.parts.associateBy { it.label }
        assertEquals(emptyList(), resources.score?.extras)
        assertEquals(emptyList(), resources.unmapped)
        assertEquals(1.0, parts.getValue("XML").score.recall, "${parts.getValue("XML").score.misses}")
        assertEquals(1.0, parts.getValue("Kotlin/Java").score.recall, "${parts.getValue("Kotlin/Java").score.misses}")
        assertEquals(0, parts.getValue("XML").evidenceMismatches)
    }

    @Test
    fun `the components of the manifest and the view binding classes are consumers too`() {
        val resources = result.relationshipClasses.single { it.name == "resource_to_consumer" }
        val parts = resources.parts.associateBy { it.label }
        assertEquals(7, parts.getValue("Manifest").score.expected)
        assertEquals(7, parts.getValue("Manifest").score.matched)
        assertEquals(14, parts.getValue("Kotlin layout (view binding)").score.expected)
        assertEquals(14, parts.getValue("Kotlin layout (view binding)").score.matched)
        assertEquals(emptyList(), resources.score?.misses)
        assertEquals(76, resources.goldCount)
    }

    @Test
    fun `kotlin and java reach their resources over a convention hop that stands for generator output`() {
        val resources = result.relationshipClasses.single { it.name == "resource_to_consumer" }
        val code = resources.parts.single { it.label == "Kotlin/Java" }
        assertEquals(0, code.evidenceMismatches)
        assertEquals(mapOf("convention" to code.score.matched), code.acceptedEvidence)
    }

    @Test
    fun `the edges to R fields in the closed-world files are scored now`() {
        val adapter = result.closedWorld.files.single { it.file.endsWith("TaskAdapter.kt") }
        assertEquals(emptyList(), adapter.deferred.filter { "R." in it }, "${adapter.deferred}")
        val fields = adapter.byKind.single { it.kind == "resource_reference" }
        assertEquals(fields.expected, fields.matched)
        assertEquals(emptyMap(), adapter.extrasByKind, "The holder classes of R are not edges gold lists")
    }
}
