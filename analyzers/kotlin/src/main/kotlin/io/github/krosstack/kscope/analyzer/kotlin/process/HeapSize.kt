package io.github.krosstack.kscope.analyzer.kotlin.process

/** The value of a `-Xmx` option: digits and an optional `k`, `m` or `g`. */
internal object HeapSize {
    private val PATTERN = Regex("(\\d+)([kKmMgG]?)")
    private const val KILOBYTE = 1L shl 10
    private const val MEGABYTE = KILOBYTE shl 10
    private const val GIGABYTE = MEGABYTE shl 10
    private const val UNKNOWN = 2 * GIGABYTE

    fun bytesOf(setting: String): Long = bytesOrNull(setting) ?: UNKNOWN

    /** The size in bytes, or null when [setting] is not a positive size in the form above that fits in a long. */
    fun bytesOrNull(setting: String): Long? {
        val match = PATTERN.matchEntire(setting.trim())
        val amount = match?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it >= 1 }
        return if (match == null || amount == null) null else multiply(amount, unitOf(match.groupValues[2]))
    }

    /** The size in whole mebibytes, rounded up, or null when [setting] is not a size. */
    fun mebibytesOf(setting: String): Int? = bytesOrNull(setting)?.let { bytes ->
        val rounded = bytes / MEGABYTE + if (bytes % MEGABYTE == 0L) 0L else 1L
        rounded.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun multiply(amount: Long, unit: Long): Long? = try {
        Math.multiplyExact(amount, unit)
    } catch (_: ArithmeticException) {
        null
    }

    private fun unitOf(suffix: String): Long = when (suffix.lowercase()) {
        "k" -> KILOBYTE
        "m" -> MEGABYTE
        "g" -> GIGABYTE
        else -> 1L
    }
}
