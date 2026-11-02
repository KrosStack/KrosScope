package io.github.krosstack.kscope.benchmark.run

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the manifest, layout, view binding and build config capabilities achieve on the reference workspace. */
class GeneratedSurfaceConformanceTest {
    private val result = ConformanceFixture.default

    private fun classNamed(name: String) = result.relationshipClasses.single { it.name == name }

    @Test
    fun `manifest components are bound to their classes by a textual match of the class name`() {
        val manifest = classNamed("manifest_component_to_class")
        assertEquals(9, manifest.goldCount)
        assertEquals(1.0, manifest.score?.precision, "${manifest.score?.extras}")
        assertEquals(1.0, manifest.score?.recall, "${manifest.score?.misses}")
        assertEquals(emptyList(), manifest.unmapped)
        assertEquals(0, manifest.evidenceMismatches)
    }

    @Test
    fun `classes named in layouts and navigation graphs are found`() {
        val layouts = classNamed("layout_class_reference")
        assertEquals(1.0, layouts.score?.precision, "${layouts.score?.extras}")
        assertEquals(1.0, layouts.score?.recall, "${layouts.score?.misses}")
        assertEquals(0, layouts.evidenceMismatches)
    }

    @Test
    fun `binding classes and their consumers are found, the hop from the layout being a convention`() {
        val bindings = classNamed("viewbinding_layout_to_consumer")
        assertEquals(1.0, bindings.score?.precision, "${bindings.score?.extras}")
        assertEquals(1.0, bindings.score?.recall, "${bindings.score?.misses}")
        assertEquals(emptyList(), bindings.unmapped)
        assertEquals(0, bindings.evidenceMismatches)
        assertEquals(mapOf("convention" to bindings.score?.matched), bindings.acceptedEvidence)
    }

    @Test
    fun `build config fields and the places that read them are found, the hop from the script being a convention`() {
        val fields = classNamed("buildconfig_field_to_consumer")
        assertEquals(1.0, fields.score?.precision, "${fields.score?.extras}")
        assertEquals(1.0, fields.score?.recall, "${fields.score?.misses}")
        assertEquals(emptyList(), fields.unmapped)
        assertEquals(0, fields.evidenceMismatches)
        assertEquals(mapOf("convention" to 6), fields.acceptedEvidence)
    }

    @Test
    fun `the edges to binding class members in the adapter are scored and the generated surfaces add no extras`() {
        val adapter = result.closedWorld.files.single { it.file.endsWith("TaskAdapter.kt") }
        assertEquals(emptyList(), adapter.deferred.filter { "Binding" in it }, "${adapter.deferred}")
        assertEquals(emptyMap(), adapter.extrasByKind)
        assertTrue(adapter.byKind.single { it.kind == "type_reference" }.matched >= 1)
    }
}
