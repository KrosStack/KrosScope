package io.github.krosstack.kscope.store.search

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.spi.search.SearchHit
import io.github.krosstack.kscope.spi.search.SearchIndex
import io.github.krosstack.kscope.spi.search.SearchQuery
import io.github.krosstack.kscope.spi.search.SearchScope
import io.github.krosstack.kscope.spi.search.SearchTerm
import io.github.krosstack.kscope.store.codec.NodeRefs
import io.github.krosstack.kscope.store.sqlite.Database

internal class SqliteSearchIndex(private val database: Database) : SearchIndex {
    override fun search(query: SearchQuery): List<SearchHit> {
        val terms = query.terms.filter { it.text.isNotBlank() && it.weight > 0.0 }
        if (terms.isEmpty() || query.limit <= 0) return emptyList()
        val layout = SearchLayouts.of(query.scope)
        val scorer = TermScorer(database, layout)
        val best = scorer.candidates(terms)
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.key })
            .take(query.limit)
        val fields = scorer.fieldScores(terms, best.map { it.doc })
        return best.map { SearchHit(nodeOf(layout, it.key), it.score, it.matched.toList(), fields.getValue(it.doc)) }
    }

    override fun vocabularyContains(term: String, scope: SearchScope): Boolean {
        if (term.isBlank()) return false
        val layout = SearchLayouts.of(scope)
        val rows = database.query(
            "SELECT 1 FROM ${layout.fts} WHERE ${layout.fts} MATCH ? LIMIT 1",
            listOf(MatchExpression.of(SearchTerm(term))),
        ) { it.long() }
        return rows.isNotEmpty()
    }

    override fun vocabularyWithPrefix(prefix: String, scope: SearchScope, limit: Int): List<String> {
        if (prefix.isEmpty() || limit <= 0) return emptyList()
        val layout = SearchLayouts.of(scope)
        val found = mutableListOf<String>()
        var offset = 0
        while (found.size < limit) {
            val page = vocabularyPage(layout, prefix, limit, offset)
            if (page.isEmpty()) break
            found += page.filter { vocabularyContains(it, scope) }.take(limit - found.size)
            offset += page.size
        }
        return found
    }

    override fun documentFrequency(term: String, scope: SearchScope): Int {
        if (term.isBlank()) return 0
        val layout = SearchLayouts.of(scope)
        return database.query(
            "SELECT COUNT(*) FROM ${layout.fts} WHERE ${layout.fts} MATCH ?",
            listOf(MatchExpression.of(SearchTerm(term))),
        ) { it.long().toInt() }.single()
    }

    private fun vocabularyPage(layout: SearchLayout, prefix: String, size: Int, offset: Int): List<String> =
        database.query(
            "SELECT term FROM ${layout.vocabulary} WHERE term >= ? AND term < ? ORDER BY doc DESC, term " +
                "LIMIT ? OFFSET ?",
            listOf(prefix, prefix + END_OF_RANGE, size, offset),
        ) { it.text() }

    private fun nodeOf(layout: SearchLayout, key: String): NodeRef = when (layout.scope) {
        SearchScope.SYMBOLS -> NodeRefs.decode("symbol", key)
        SearchScope.ARTIFACTS -> NodeRefs.decode("artifact", key)
    }

    private companion object {
        val END_OF_RANGE = String(Character.toChars(Character.MAX_CODE_POINT))
    }
}

/** The FTS5 query for a term: a phrase, so that the tokenizer of the table splits and stems it as it did the text. */
internal object MatchExpression {
    fun of(term: SearchTerm): String {
        val phrase = "\"" + term.text.replace("\"", "\"\"") + "\""
        return if (term.prefix) "$phrase*" else phrase
    }
}
