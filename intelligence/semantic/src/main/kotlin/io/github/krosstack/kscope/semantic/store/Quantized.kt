package io.github.krosstack.kscope.semantic.store

import kotlin.math.abs
import kotlin.math.round

public class Quantized(bytes: ByteArray, public val scale: Float) {
    private val values = bytes.copyOf()
    public val bytes: ByteArray get() = values.copyOf()
    public val dimensions: Int get() = values.size

    init {
        require(scale.isFinite() && scale >= 0 && values.none { it == Byte.MIN_VALUE })
        require(scale != 0f || values.all { it == 0.toByte() })
        require(values.size <= Int.MAX_VALUE / (MAX_VALUE * MAX_VALUE))
    }

    public fun dot(other: Quantized): Double {
        require(dimensions == other.dimensions)
        var sum = 0
        for (index in values.indices) sum += values[index].toInt() * other.values[index].toInt()
        return sum.toDouble() * scale.toDouble() * other.scale.toDouble()
    }

    public companion object {
        private const val MAX_VALUE = 127

        public fun quantize(vector: FloatArray): Quantized {
            require(vector.all { it.isFinite() })
            val maximum = vector.fold(0f) { largest, value -> maxOf(largest, abs(value)) }
            val scale = maximum / MAX_VALUE
            val bytes = ByteArray(vector.size) { index ->
                if (scale == 0f) 0 else round(vector[index] / scale).toInt().coerceIn(-MAX_VALUE, MAX_VALUE).toByte()
            }
            return Quantized(bytes, scale)
        }
    }
}
