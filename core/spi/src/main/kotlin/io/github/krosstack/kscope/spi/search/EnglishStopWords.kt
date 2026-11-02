package io.github.krosstack.kscope.spi.search

public object EnglishStopWords {
    public val words: Set<String> = setOf(
        "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in", "into", "is", "it", "no", "not",
        "of", "on", "or", "such", "that", "the", "their", "then", "there", "these", "they", "this", "to", "was",
        "will", "with",
    )

    public fun remove(tokens: Iterable<String>): List<String> = tokens.filterNot { it.lowercase() in words }
}
