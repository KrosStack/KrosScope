package io.github.krosstack.kscope.analyzer.history

import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryAnalysisTest {
    private fun commit(author: String, vararg files: String) = GitCommit(author, files.toList())

    @Test
    fun `the log is read into commits with their author and files`() {
        val output = "\u001Eaaa\u001Falice@example.invalid\n\nA.kt\nsrc/B.kt\n" +
            "\u001Ebbb\u001Fbob@example.invalid\n\nA.kt\n"
        val commits = CommitLog.parse(output)
        assertEquals(listOf("alice@example.invalid", "bob@example.invalid"), commits.map { it.author })
        assertEquals(listOf(listOf("A.kt", "src/B.kt"), listOf("A.kt")), commits.map { it.files })
    }

    @Test
    fun `an empty log and a commit without files are read`() {
        assertEquals(emptyList(), CommitLog.parse(""))
        assertEquals(listOf(emptyList()), CommitLog.parse("\u001Eaaa\u001Fa@example.invalid\n\n").map { it.files })
    }

    @Test
    fun `file names with spaces and non ascii letters survive`() {
        val commits = CommitLog.parse("\u001Eaaa\u001Fa@x\n\nmy file.kt\nüber/日本.kt\n")
        assertEquals(listOf("my file.kt", "über/日本.kt"), commits.single().files)
    }

    @Test
    fun `a commit seen from a directory keeps the files below it and the size of the whole`() {
        val seen = commit("a", "app/A.kt", "app/sub/B.kt", "lib/C.kt").within("app/")
        assertEquals(listOf("A.kt", "sub/B.kt"), seen.files)
        assertEquals(3, seen.size)
    }

    @Test
    fun `the support threshold decides which pairs are related`() {
        val commits = listOf(commit("a", "X", "Y"), commit("a", "X", "Z"), commit("a", "X", "Y"))
        val pairs = HistoryAnalysis.summarize(commits, setOf("X", "Y", "Z")).coChanges.map { it.from to it.to }
        assertEquals(listOf("X" to "Y", "Y" to "X"), pairs)
    }

    @Test
    fun `the confidence is exact up to four decimals`() {
        val commits = List(2) { commit("a", "X", "Y") } + commit("a", "X")
        val related = HistoryAnalysis.summarize(commits, setOf("X", "Y")).coChanges
        assertEquals(mapOf("X" to "0.6667", "Y" to "1.0000"), related.associate { it.from to it.confidence })
    }

    @Test
    fun `files whose newest commit is the same share a recency rank`() {
        val commits = listOf(commit("a", "X", "Y"), commit("b", "Z"), commit("a", "X", "Z", "W"))
        val files = HistoryAnalysis.summarize(commits, setOf("X", "Y", "Z", "W")).files
        assertEquals(mapOf("W" to 2, "X" to 0, "Y" to 0, "Z" to 1), files.mapValues { it.value.recencyRank })
    }
}
