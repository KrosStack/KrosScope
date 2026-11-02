package io.github.krosstack.kscope.spi.search

import io.github.krosstack.kscope.model.NodeRef

public enum class SearchScope {
    SYMBOLS,
    ARTIFACTS,
}

/** [text] is already normalized by the caller, with [CodeTokenizer]. A [prefix] term matches every term it starts. */
public data class SearchTerm(
    public val text: String,
    public val weight: Double = 1.0,
    public val prefix: Boolean = false,
)

public data class SearchQuery(
    public val terms: List<SearchTerm>,
    public val scope: SearchScope,
    public val limit: Int = 50,
)

/**
 * [score] is the sum over the matched terms of the term weight times the field-weighted BM25 score of the term.
 * [fieldScores] splits it by field, approximately, and leaves out the fields that did not match.
 */
public data class SearchHit(
    public val node: NodeRef,
    public val score: Double,
    public val matchedTerms: List<String>,
    public val fieldScores: Map<String, Double>,
)

/**
 * Lexical search over the symbols and the artifacts of a graph. The index stems what it holds and what it is asked
 * for, so a term matches every word that shares its stem, and the terms of the vocabulary are stems.
 */
public interface SearchIndex {
    /** Hits ordered by score, highest first, then by node key. */
    public fun search(query: SearchQuery): List<SearchHit>

    /** True when at least one document of [scope] contains [term], or a word with its stem. */
    public fun vocabularyContains(term: String, scope: SearchScope): Boolean

    /**
     * The terms of the index that start with [prefix], those in most documents first, then alphabetically. The terms
     * are stems; each of them is found when it is searched for as it is.
     */
    public fun vocabularyWithPrefix(prefix: String, scope: SearchScope, limit: Int = 20): List<String>

    /** The number of documents of [scope] that contain [term], or a word with its stem. */
    public fun documentFrequency(term: String, scope: SearchScope): Int
}
