package io.github.krosstack.kscope.semantic.store

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuantizedTest {
    @Test
    fun `scale uses max abs and ties round to even symmetrically`() {
        val vector = Quantized.quantize(floatArrayOf(127f, -127f, 0.5f, 1.5f, 2.5f, -1.5f))
        assertEquals(1f, vector.scale)
        assertContentEquals(byteArrayOf(127, -127, 0, 2, 2, -2), vector.bytes)
    }

    @Test
    fun `zero stays zero with zero scale`() {
        val vector = Quantized.quantize(FloatArray(8))
        assertEquals(0f, vector.scale)
        assertContentEquals(ByteArray(8), vector.bytes)
        assertEquals(0.0, vector.dot(vector))
    }

    @Test
    fun `integer dot product agrees with fixed order dequantized float reference`() {
        val left = Quantized.quantize(floatArrayOf(-2f, 0.2f, 4f))
        val right = Quantized.quantize(floatArrayOf(1f, -3f, 2f))
        val reference = left.bytes.indices.sumOf {
            (left.bytes[it] * left.scale).toDouble() * (right.bytes[it] * right.scale).toDouble()
        }
        assertEquals(reference, left.dot(right), 0.00001)
    }

    @Test
    fun `bytes returns a defensive copy`() {
        val vector = Quantized.quantize(floatArrayOf(-2f, 0.2f, 4f))
        val bytes = vector.bytes
        bytes[0] = 0
        assertTrue(vector.bytes[0] != 0.toByte())
    }
}
