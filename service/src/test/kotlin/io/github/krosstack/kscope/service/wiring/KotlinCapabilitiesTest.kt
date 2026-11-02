package io.github.krosstack.kscope.service.wiring

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinCapabilitiesTest {
    private val registry = DefaultCapabilities.registry(Path.of("index"))

    @Test
    fun `the compose resource capabilities are registered`() {
        val detectors = registry.detectors.map { it.id }
        val expected = listOf("compose.resource.values", "compose.resource.drawable", "compose.resource.font")
        assertTrue(detectors.containsAll(expected + "compose.resource.file"), "$detectors")
        assertTrue("kscope.compose.resources" in registry.producers.map { it.descriptor.producer.id })
        assertTrue("kscope.compose.res" in registry.synthesizers.map { it.descriptor.producer.id })
    }

    @Test
    fun `the linkers of the Kotlin facts are registered and claim no symbolic reference namespace`() {
        val linkers = registry.linkers.associate { it.descriptor.producer.id to it.namespaces }
        val ids = listOf(
            "kscope.kotlin.expect-actual-linker",
            "kscope.kotlin.variant-alternative-linker",
            "kscope.kotlin.test-subject-linker",
            "kscope.kotlin.qualified-name-linker",
        )
        assertTrue(linkers.keys.containsAll(ids), "${linkers.keys}")
        ids.forEach { assertEquals(emptySet(), linkers[it], it) }
    }
}
