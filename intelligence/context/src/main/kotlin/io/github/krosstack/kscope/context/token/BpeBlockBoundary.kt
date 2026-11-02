package io.github.krosstack.kscope.context.token

/**
 * JTokkit 1.1.0's ordinary o200k/cl100k pretokens stop after double LF before ASCII words or backticks:
 * word prefixes exclude CR/LF, punctuation tails accept only CR/LF (and o200k slash), and
 * whitespace branches cannot consume the next non-whitespace. BPE never merges across pretokens.
 */
internal object BpeBlockBoundary {
    fun safe(parts: List<String>): Boolean = parts.all(::validUnicode) && parts.zipWithNext().all { (left, right) ->
        left.endsWith("\n\n") && safeStart(right.firstOrNull())
    }

    fun standalone(block: String): Boolean =
        validUnicode(block) && block.endsWith("\n\n") && safeStart(block.firstOrNull())

    fun valid(block: String): Boolean = validUnicode(block)

    private fun safeStart(character: Char?): Boolean =
        character == '`' || character in 'A'..'Z' || character in 'a'..'z'

    private fun validUnicode(text: String): Boolean {
        var offset = 0
        var valid = true
        while (offset < text.length && valid) {
            val character = text[offset]
            when {
                character.isHighSurrogate() -> {
                    valid = text.getOrNull(offset + 1)?.isLowSurrogate() == true
                    offset += 2
                }

                character.isLowSurrogate() -> valid = false

                else -> offset++
            }
        }
        return valid
    }
}
