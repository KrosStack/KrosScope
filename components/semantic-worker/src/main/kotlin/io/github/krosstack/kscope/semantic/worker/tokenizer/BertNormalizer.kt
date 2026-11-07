package io.github.krosstack.kscope.semantic.worker.tokenizer

import java.text.Normalizer
import java.util.Locale

internal class BertNormalizer(private val file: TokenizerFile) {
    fun normalize(text: String, originalIndices: MutableList<Int>? = null): String = buildString {
        var index = 0
        text.codePoints().forEach { point ->
            val normalized = normalizePoint(point)
            append(normalized)
            repeat(normalized.length) { originalIndices?.add(index) }
            index += Character.charCount(point)
        }
    }

    private fun normalizePoint(point: Int): String {
        val cleaned = clean(point)
        val stripped = if (file.stripAccents) {
            buildString {
                Normalizer.normalize(cleaned, Normalizer.Form.NFD).codePoints()
                    .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
                    .forEach { appendCodePoint(it) }
            }
        } else {
            cleaned
        }
        return if (file.lowercase) stripped.lowercase(Locale.ROOT) else stripped
    }

    private fun clean(point: Int): String = when {
        file.cleanText && discarded(point) -> ""
        file.cleanText && whitespace(point) -> " "
        file.chinese && chinese(point) -> " ${String(Character.toChars(point))} "
        else -> String(Character.toChars(point))
    }

    private fun discarded(point: Int): Boolean = point == 0 || point == REPLACEMENT ||
        (point !in ASCII_WHITESPACE && Character.getType(point) in CONTROL_TYPES)

    private fun whitespace(point: Int): Boolean = point in ASCII_WHITESPACE ||
        Character.isSpaceChar(point)

    private fun chinese(point: Int): Boolean = CHINESE_RANGES.any { point in it }

    private companion object {
        const val REPLACEMENT = 0xfffd
        val ASCII_WHITESPACE = setOf(0x09, 0x0a, 0x0d, 0x20)
        val CONTROL_TYPES = setOf(
            Character.CONTROL.toInt(),
            Character.FORMAT.toInt(),
            Character.PRIVATE_USE.toInt(),
            Character.SURROGATE.toInt(),
        )
        val CHINESE_RANGES = listOf(
            0x4e00..0x9fff,
            0x3400..0x4dbf,
            0x20000..0x2a6df,
            0x2a700..0x2b73f,
            0x2b740..0x2b81f,
            0x2b820..0x2ceaf,
            0xf900..0xfaff,
            0x2f800..0x2fa1f,
        )
    }
}
