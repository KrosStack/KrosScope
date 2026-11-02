package io.github.krosstack.kscope.spi.search

/**
 * Turns code, identifiers, paths and prose into lowercase index terms. Indexing and querying both use it, so a query
 * term meets the term that the same text produced in the index.
 *
 * A word is a run of letters, digits, `_` and `-`; every other character separates words, so dots and slashes end a
 * word. A word splits at `_` and `-`, at a lowercase letter followed by an uppercase one, before the last capital
 * of an acronym that a lowercase letter follows, and between letters and digits. An identifier of several parts is
 * also kept whole, with the separators removed, so `max_retry_count` and `maxRetryCount` share the term
 * `maxretrycount`. Terms shorter than two characters are dropped.
 */
public object CodeTokenizer {
    private const val MIN_TERM_LENGTH = 2
    private val wordPattern = Regex("[\\p{L}\\p{N}_\\-]+")

    /** The whole identifier, when it has several parts, followed by its parts, for each word of [text] in order. */
    public fun tokens(text: String): List<String> = words(text).flatMap(Word::tokens).filter(::isTerm)

    /** The parts of each word of [text], without the whole identifiers. */
    public fun parts(text: String): List<String> = words(text).flatMap(Word::parts).filter(::isTerm)

    /** The whole identifier of each word of [text]: its parts joined, without separators. */
    public fun identifiers(text: String): List<String> = words(text).map(Word::whole).filter(::isTerm)

    /** [tokens] as one string for a full-text index; blank for null. */
    public fun indexText(text: String?): String = text?.let { tokens(it).joinToString(" ") }.orEmpty()

    private fun words(text: String): List<Word> = wordPattern.findAll(text)
        .map { match -> match.value.split('_', '-').filter { it.isNotEmpty() } }
        .filter { it.isNotEmpty() }
        .map(::Word)
        .toList()

    private fun isTerm(term: String): Boolean = term.codePointCount(0, term.length) >= MIN_TERM_LENGTH

    private class Word(segments: List<String>) {
        val whole: String = segments.joinToString("").lowercase()
        val parts: List<String> = segments.flatMap(::split).map { it.lowercase() }

        fun tokens(): List<String> = if (parts.size > 1) listOf(whole) + parts else parts
    }

    private fun split(segment: String): List<String> {
        val boundaries = (1 until segment.length).filter { isBoundary(segment, it) }
        return (listOf(0) + boundaries + segment.length).zipWithNext { start, end -> segment.substring(start, end) }
    }

    private fun isBoundary(segment: String, index: Int): Boolean {
        val previous = segment[index - 1]
        val current = segment[index]
        return when {
            previous.isDigit() != current.isDigit() -> true
            previous.isLowerCase() && current.isUpperCase() -> true
            previous.isUpperCase() && current.isUpperCase() -> segment.getOrNull(index + 1)?.isLowerCase() == true
            else -> false
        }
    }
}
