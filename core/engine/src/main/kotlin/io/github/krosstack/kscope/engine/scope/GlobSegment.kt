package io.github.krosstack.kscope.engine.scope

/** Matches one path segment against a segment pattern in which only `*` and `?` are special. */
internal object GlobSegment {
    fun matches(pattern: String, text: String): Boolean {
        var patternIndex = 0
        var textIndex = 0
        var starIndex = NONE
        var resumeIndex = 0
        while (textIndex < text.length) {
            val current = pattern.getOrNull(patternIndex)
            when {
                current == '*' -> {
                    starIndex = patternIndex++
                    resumeIndex = textIndex
                }

                current != null && (current == '?' || current == text[textIndex]) -> {
                    patternIndex++
                    textIndex++
                }

                starIndex != NONE -> {
                    patternIndex = starIndex + 1
                    textIndex = ++resumeIndex
                }

                else -> return false
            }
        }
        return pattern.substring(patternIndex).all { it == '*' }
    }

    private const val NONE = -1
}
