package io.github.krosstack.kscope.store.search

import io.github.krosstack.kscope.spi.search.SearchTerm
import io.github.krosstack.kscope.store.sqlite.Database

internal class Candidate(val doc: Long, val key: String) {
    var score = 0.0
        private set
    val matched = LinkedHashSet<String>()

    fun add(term: SearchTerm, termScore: Double) {
        score += term.weight * termScore
        matched += term.text
    }
}

/**
 * Scores the documents of one set for the terms of a query. Each term is run as its own query, because the BM25
 * function of FTS5 cannot weigh terms differently; the scores of a document are added up in the order of the terms.
 *
 * FTS5 weighs the columns before it saturates the term frequency, so a score is not a sum over columns. The scores per
 * field are an approximation: the score of a term is split in proportion to what each column alone would have
 * scored, by one more query per term over the documents of the best hits only.
 */
internal class TermScorer(private val database: Database, private val layout: SearchLayout) {
    private class Match(val doc: Long, val key: String, val score: Double)

    private companion object {
        const val DOC_CHUNK = 500
    }

    fun candidates(terms: List<SearchTerm>): Collection<Candidate> {
        val byDoc = LinkedHashMap<Long, Candidate>()
        terms.forEach { term ->
            matches(term).forEach { match ->
                byDoc.getOrPut(match.doc) { Candidate(match.doc, match.key) }.add(term, match.score)
            }
        }
        return byDoc.values
    }

    fun fieldScores(terms: List<SearchTerm>, docs: List<Long>): Map<Long, Map<String, Double>> {
        if (docs.isEmpty()) return emptyMap()
        val sums = docs.associateWith { DoubleArray(layout.columns.size) }
        terms.forEach { term ->
            docs.chunked(DOC_CHUNK).flatMap { termScoreAndScoreOfEachColumn(term, it) }.forEach { (doc, scores) ->
                splitOverColumns(sums.getValue(doc), term.weight, scores.first(), scores.drop(1))
            }
        }
        return sums.mapValues { (_, sum) -> matchedColumns(sum) }
    }

    private fun matches(term: SearchTerm): List<Match> = database.query(
        "SELECT s.doc, d.node_key, s.score FROM " +
            "(SELECT rowid AS doc, ${layout.scoreSql} AS score FROM ${layout.fts} WHERE ${layout.fts} MATCH ?) s " +
            "JOIN ${layout.documents} d ON d.doc = s.doc",
        listOf(MatchExpression.of(term)),
    ) { Match(it.long(), it.text(), it.double()) }

    private fun termScoreAndScoreOfEachColumn(term: SearchTerm, docs: List<Long>): List<Pair<Long, List<Double>>> {
        val marks = docs.joinToString(", ") { "?" }
        val scores = (listOf(layout.scoreSql) + layout.columnScoreSql).joinToString(", ")
        return database.query(
            "SELECT rowid, $scores FROM ${layout.fts} WHERE ${layout.fts} MATCH ? AND rowid IN ($marks)",
            listOf(MatchExpression.of(term)) + docs,
        ) { cursor -> cursor.long() to List(layout.columns.size + 1) { cursor.double() } }
    }

    private fun splitOverColumns(sums: DoubleArray, weight: Double, total: Double, perColumn: List<Double>) {
        val whole = perColumn.sum()
        if (whole <= 0.0) return
        perColumn.forEachIndexed { column, score -> sums[column] += weight * total * score / whole }
    }

    private fun matchedColumns(sums: DoubleArray): Map<String, Double> {
        val scores = LinkedHashMap<String, Double>()
        layout.columns.forEachIndexed { index, column -> if (sums[index] > 0.0) scores[column] = sums[index] }
        return scores
    }
}
