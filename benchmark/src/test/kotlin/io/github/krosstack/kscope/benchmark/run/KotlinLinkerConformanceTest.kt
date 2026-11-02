package io.github.krosstack.kscope.benchmark.run

import io.github.krosstack.kscope.benchmark.score.ClassScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the resource stub, the flavor alternatives and the test linker achieve on the reference workspace. */
class KotlinLinkerConformanceTest {
    private val result = ConformanceFixture.default

    private fun classOf(name: String): ClassScore = result.relationshipClasses.single { it.name == name }

    @Test
    fun `every string and drawable of Compose resources reaches the lines that read it`() {
        val compose = classOf("compose_resource_to_consumer")
        assertEquals(7, compose.goldCount)
        assertEquals(emptyList(), compose.score?.misses)
        assertEquals(emptyList(), compose.score?.extras)
        assertEquals(emptyList(), compose.unmapped)
        assertEquals(0, compose.evidenceMismatches)
        assertEquals(mapOf("convention" to 7), compose.acceptedEvidence)
    }

    @Test
    fun `every flavor implementation is reached from the interface and from the usage site`() {
        val flavors = classOf("flavor_specific_implementation")
        assertEquals(emptyList(), flavors.score?.misses)
        assertEquals(emptyList(), flavors.score?.extras)
        assertEquals(emptyList(), flavors.unmapped)
        assertEquals(0, flavors.evidenceMismatches)
    }

    @Test
    fun `every test class is bound to the class or function that gold names`() {
        val tests = classOf("test_to_subject")
        assertEquals(18, tests.goldCount)
        assertEquals(emptyList(), tests.score?.misses)
        assertEquals(emptyList(), tests.score?.extras)
        assertEquals(emptyList(), tests.unmapped)
        assertEquals(0, tests.evidenceMismatches)
    }

    @Test
    fun `the overrides in the object expressions of the sync client are found`() {
        val client = result.closedWorld.files.single { it.file.endsWith("SyncServiceClient.kt") }
        val overrides = client.byKind.single { it.kind == "override" }
        assertEquals(overrides.expected, overrides.matched)
        assertTrue(client.score.recall == 1.0, "${client.score.misses}")
    }

    @Test
    fun `a test edge is no reference in the closed-world files`() {
        val formatterTest = result.closedWorld.files.single { it.file.endsWith("LegacyTaskFormatterTest.java") }
        assertEquals(emptyList(), formatterTest.score.extras)
    }
}
