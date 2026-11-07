package io.github.krosstack.kscope.semantic.worker.tokenizer

internal object BertPreTokenizer {
    fun split(text: String): List<String> = buildList {
        val word = StringBuilder()
        text.codePoints().forEach { point ->
            when {
                Character.isWhitespace(point) -> {
                    if (word.isNotEmpty()) add(word.toString())
                    word.clear()
                }

                punctuation(point) -> {
                    if (word.isNotEmpty()) add(word.toString())
                    word.clear()
                    add(String(Character.toChars(point)))
                }

                else -> word.appendCodePoint(point)
            }
        }
        if (word.isNotEmpty()) add(word.toString())
    }

    private fun punctuation(point: Int): Boolean = ASCII_PUNCTUATION.any { point in it } ||
        Character.getType(point) in PUNCTUATION_TYPES

    private val ASCII_PUNCTUATION = listOf(33..47, 58..64, 91..96, 123..126)
    private val PUNCTUATION_TYPES = setOf(
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
    )
}
