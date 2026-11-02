package io.github.krosstack.kscope.retrieval.analysis

import io.github.krosstack.kscope.retrieval.AnalyzedQuery
import io.github.krosstack.kscope.retrieval.ArtifactVocabulary
import io.github.krosstack.kscope.retrieval.QueryExpansion
import io.github.krosstack.kscope.spi.search.CodeTokenizer
import io.github.krosstack.kscope.spi.search.EnglishStopWords
import io.github.krosstack.kscope.spi.search.SearchIndex
import io.github.krosstack.kscope.spi.search.SearchScope
import io.github.krosstack.kscope.spi.search.SearchTerm

internal class QueryAnalyzer(private val index: SearchIndex) {
    fun analyze(text: String): AnalyzedQuery {
        val terms = EnglishStopWords.remove(CodeTokenizer.tokens(text)).distinct()
        val mentions = MENTIONS.findAll(text).map { it.value.trim('"', '`') }.distinct().toList()
        val expansions = terms.filterNot(::known).flatMap(::bridge)
        return AnalyzedQuery(text, terms, mentions, expansions, ArtifactVocabulary.hints(text))
    }

    private fun known(term: String): Boolean = SearchScope.entries.any { index.vocabularyContains(term, it) }

    private fun bridge(term: String): List<QueryExpansion> {
        val spelling = term.replace("isation", "ization").replace("colour", "color").replace("behaviour", "behavior")
        val abbreviations = ABBREVIATIONS.filter { term in it }.flatten().filter { it != term && known(it) }
        val prefix = (term.length - 1 downTo MIN_PREFIX).map { term.take(it) }.firstOrNull(::known)
        return when {
            spelling != term && known(spelling) -> listOf(QueryExpansion(term, spelling, "spelling"))

            abbreviations.isNotEmpty() -> abbreviations.map { QueryExpansion(term, it, "abbreviation") }

            prefix != null -> listOf(QueryExpansion(term, prefix, "prefix"))

            term.length < MIN_PREFIX -> emptyList()

            else -> SearchScope.entries.flatMap { index.vocabularyWithPrefix(term, it, MAX_EXPANSIONS) }
                .distinct().sorted().take(MAX_EXPANSIONS).map { QueryExpansion(term, it, "completion") }
        }
    }

    companion object {
        private const val MIN_PREFIX = 4
        private const val MAX_EXPANSIONS = 3
        private const val EXPANSION_WEIGHT = 0.45
        private val MENTIONS = Regex(
            "\"[^\"]+\"|`[^`]+`|[A-Za-z0-9_]+(?:[./][A-Za-z0-9_]+)+|[A-Z][a-z]+[A-Z]\\w*",
        )
        private val ABBREVIATIONS = listOf(
            setOf("sync", "synchronization", "synchronisation", "synchronize"),
            setOf("config", "configuration"), setOf("prefs", "preferences"),
            setOf("auth", "authentication", "authorization"), setOf("repo", "repository"),
            setOf("db", "database"), setOf("nav", "navigation"), setOf("impl", "implementation"),
            setOf("init", "initialization"), setOf("msg", "message"),
        )

        fun searchTerms(query: AnalyzedQuery): List<SearchTerm> = query.terms.map { SearchTerm(it) } +
            query.expansions.map { SearchTerm(it.replacement, EXPANSION_WEIGHT) }
    }
}
