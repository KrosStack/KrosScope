package io.github.krosstack.kscope.spi

import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProducerDescriptorTest {
    private fun descriptor(granularity: OwnershipKind, inputs: InputBasis? = null) = when (inputs) {
        null -> ProducerDescriptor(ProducerRef("test", "1"), emptySet(), emptySet(), granularity)
        else -> ProducerDescriptor(ProducerRef("test", "1"), emptySet(), emptySet(), granularity, inputs)
    }

    @Test
    fun `a producer depends on the content of its artifacts unless it says otherwise`() {
        assertEquals(InputBasis.ARTIFACT_CONTENT, descriptor(OwnershipKind.ARTIFACT).inputs)
    }

    @Test
    fun `a producer that depends on the repository revision owns repository units`() {
        assertEquals(
            InputBasis.REPOSITORY_REVISION,
            descriptor(OwnershipKind.REPOSITORY, InputBasis.REPOSITORY_REVISION).inputs,
        )
    }

    @Test
    fun `a producer cannot depend on the repository revision and own other units`() {
        OwnershipKind.entries.filter { it != OwnershipKind.REPOSITORY }.forEach { granularity ->
            assertFailsWith<IllegalArgumentException> { descriptor(granularity, InputBasis.REPOSITORY_REVISION) }
        }
    }
}
