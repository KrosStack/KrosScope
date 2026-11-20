package io.github.krosstack.kscope.semantic.worker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PipelineIdentityTest {
    private val descriptor = ModelDescriptor("test", "revision", 2, "int8", "cls", "query: ", 512, emptyMap())

    @Test
    fun `int8 cpu fp16 cuda and fp32 cpu have distinct identities`() {
        val identities = listOf(
            descriptor.pipelineIdentity("cpu", "int8"),
            descriptor.copy(quantization = "fp16").pipelineIdentity("cuda", "fp16"),
            descriptor.copy(quantization = "fp32").pipelineIdentity("cpu", "fp32"),
        )
        assertEquals(3, identities.toSet().size)
    }

    @Test
    fun `device separates the same float descriptor`() {
        val float = descriptor.copy(quantization = "fp32")
        assertNotEquals(float.pipelineIdentity("cpu", "descriptor"), float.pipelineIdentity("cuda", "descriptor"))
    }

    @Test
    fun `descriptor changes invalidate float identity`() {
        val float = descriptor.copy(quantization = "fp32")
        assertNotEquals(float.pipelineIdentity("cpu", "one"), float.pipelineIdentity("cpu", "two"))
    }

    @Test
    fun `legacy int8 cpu identity is unchanged`() {
        assertEquals(descriptor.identity, descriptor.pipelineIdentity("cpu", "descriptor"))
    }
}
