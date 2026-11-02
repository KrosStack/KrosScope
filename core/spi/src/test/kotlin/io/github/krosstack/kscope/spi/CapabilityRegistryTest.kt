package io.github.krosstack.kscope.spi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CapabilityRegistryTest {
    @Test
    fun `an empty registry holds no capabilities`() {
        val registry = CapabilityRegistry()
        val everything = listOf(
            registry.detectors,
            registry.buildModelProviders,
            registry.synthesizers,
            registry.producers,
            registry.linkers,
        )
        assertTrue(everything.all { it.isEmpty() })
    }

    @Test
    fun `detectors are ordered by id regardless of registration order`() {
        val registry = CapabilityRegistry(detectors = listOf("kotlin", "android", "proto").map(::FakeDetector))
        assertEquals(listOf("android", "kotlin", "proto"), registry.detectors.map { it.id })
    }

    @Test
    fun `every other capability kind is ordered by producer id`() {
        val registry = CapabilityRegistry(
            buildModelProviders = listOf("b-c", "b-a", "b-b").map(::FakeBuildModelProvider),
            synthesizers = listOf("s-c", "s-a", "s-b").map(::FakeSynthesizer),
            producers = listOf("p-c", "p-a", "p-b").map(::FakeProducer),
            linkers = listOf("l-c", "l-a", "l-b").map(::FakeLinker),
        )
        assertEquals(listOf("b-a", "b-b", "b-c"), registry.buildModelProviders.map { it.producer.id })
        assertEquals(listOf("s-a", "s-b", "s-c"), registry.synthesizers.map { it.descriptor.producer.id })
        assertEquals(listOf("p-a", "p-b", "p-c"), registry.producers.map { it.descriptor.producer.id })
        assertEquals(listOf("l-a", "l-b", "l-c"), registry.linkers.map { it.descriptor.producer.id })
    }

    @Test
    fun `different registration orders yield the same registry order`() {
        val forward = CapabilityRegistry(producers = listOf("x", "y", "z").map(::FakeProducer))
        val backward = CapabilityRegistry(producers = listOf("z", "y", "x").map(::FakeProducer))
        assertEquals(
            forward.producers.map { it.descriptor.producer.id },
            backward.producers.map { it.descriptor.producer.id },
        )
    }

    @Test
    fun `duplicate ids within one kind are rejected`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            CapabilityRegistry(detectors = listOf("kotlin", "java", "kotlin").map(::FakeDetector))
        }
        assertEquals("Duplicate artifact detector id: kotlin", failure.message)
    }

    @Test
    fun `duplicate producer ids are rejected for every producing kind`() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityRegistry(producers = listOf("p", "p").map(::FakeProducer))
        }
        assertFailsWith<IllegalArgumentException> {
            CapabilityRegistry(linkers = listOf("l", "l").map(::FakeLinker))
        }
        assertFailsWith<IllegalArgumentException> {
            CapabilityRegistry(synthesizers = listOf("s", "s").map(::FakeSynthesizer))
        }
        assertFailsWith<IllegalArgumentException> {
            CapabilityRegistry(buildModelProviders = listOf("b", "b").map(::FakeBuildModelProvider))
        }
    }

    @Test
    fun `a producer id shared by two kinds that store facts is rejected`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            CapabilityRegistry(producers = listOf(FakeProducer("shared")), linkers = listOf(FakeLinker("shared")))
        }
        assertEquals("Producer id shared is used by a fact producer and a boundary linker", failure.message)
        assertFailsWith<IllegalArgumentException> {
            CapabilityRegistry(
                buildModelProviders = listOf(FakeBuildModelProvider("shared")),
                synthesizers = listOf(FakeSynthesizer("shared")),
            )
        }
    }

    @Test
    fun `a detector id may equal a producer id because detectors store no facts`() {
        val registry = CapabilityRegistry(
            detectors = listOf(FakeDetector("kotlin")),
            producers = listOf(FakeProducer("kotlin")),
        )
        assertEquals(listOf("kotlin"), registry.detectors.map { it.id })
        assertEquals(listOf("kotlin"), registry.producers.map { it.descriptor.producer.id })
    }

    @Test
    fun `later changes to the source list do not affect the registry`() {
        val source = mutableListOf(FakeDetector("b"), FakeDetector("a"))
        val registry = CapabilityRegistry(detectors = source)
        source.clear()
        assertEquals(listOf("a", "b"), registry.detectors.map { it.id })
    }

    @Test
    fun `a symbolic reference namespace may be claimed by one linker only`() {
        val registry = CapabilityRegistry(
            linkers = listOf(FakeLinker("a", setOf("one")), FakeLinker("b", setOf("two", "three"))),
        )
        assertEquals(listOf("a", "b"), registry.linkers.map { it.descriptor.producer.id })
        val failure = assertFailsWith<IllegalArgumentException> {
            CapabilityRegistry(linkers = listOf(FakeLinker("a", setOf("one", "two")), FakeLinker("b", setOf("two"))))
        }
        assertEquals("Symbolic reference namespace two is claimed by more than one linker", failure.message)
    }
}
