package io.github.krosstack.kscope.analyzer.history

import java.math.BigDecimal
import java.math.RoundingMode

internal class FileHistory(val commits: Int, val authors: Int, val recencyRank: Int)

/** [support] is the number of commits that changed both files; [confidence] says how often [from] came with [to]. */
internal class CoChange(val from: String, val to: String, val support: Int, val confidence: String)

internal class HistorySummary(val files: Map<String, FileHistory>, val coChanges: List<CoChange>)

/**
 * Turns commits into per-file statistics and co-change pairs, for the files of an inventory only. Commits that touch
 * more files than [MAX_FILES_PER_COMMIT] are skipped whole: a mass edit says nothing about which files belong together.
 * Files whose newest commit is the same share the recency rank: the number of newer commits that touched any file.
 */
internal object HistoryAnalysis {
    const val MAX_FILES_PER_COMMIT = 50
    const val MIN_SUPPORT = 2
    private const val CONFIDENCE_SCALE = 4

    /** [commits] come newest first and name their files relative to the inventory; [inventory] holds those paths. */
    fun summarize(commits: List<GitCommit>, inventory: Set<String>): HistorySummary {
        val touching = commits.filter { it.size <= MAX_FILES_PER_COMMIT }
            .map { Touch(it.author, it.files.filter { path -> path in inventory }.sorted()) }
            .filter { it.files.isNotEmpty() }
        return HistorySummary(fileHistories(touching), coChanges(touching))
    }

    private class Touch(val author: String, val files: List<String>)

    private fun fileHistories(touching: List<Touch>): Map<String, FileHistory> {
        val commits = HashMap<String, Int>()
        val authors = HashMap<String, MutableSet<String>>()
        val rank = HashMap<String, Int>()
        touching.forEachIndexed { newer, touch ->
            touch.files.forEach { file ->
                commits.merge(file, 1, Int::plus)
                authors.getOrPut(file) { HashSet() } += touch.author
                rank.putIfAbsent(file, newer)
            }
        }
        return commits.keys.sorted().associateWith {
            FileHistory(commits.getValue(it), authors.getValue(it).size, rank.getValue(it))
        }
    }

    private fun coChanges(touching: List<Touch>): List<CoChange> {
        val commits = touching.flatMap { it.files }.groupingBy { it }.eachCount()
        val support = HashMap<Pair<String, String>, Int>()
        touching.forEach { touch ->
            touch.files.forEachIndexed { index, from ->
                touch.files.drop(index + 1).forEach { to -> support.merge(from to to, 1, Int::plus) }
            }
        }
        return support.filterValues { it >= MIN_SUPPORT }.flatMap { (pair, count) ->
            listOf(
                CoChange(pair.first, pair.second, count, ratio(count, commits.getValue(pair.first))),
                CoChange(pair.second, pair.first, count, ratio(count, commits.getValue(pair.second))),
            )
        }.sortedWith(compareBy({ it.from }, { it.to }))
    }

    private fun ratio(support: Int, commits: Int): String =
        BigDecimal(support).divide(BigDecimal(commits), CONFIDENCE_SCALE, RoundingMode.HALF_UP).toPlainString()
}
