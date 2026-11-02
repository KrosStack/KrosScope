package io.github.krosstack.kscope.benchmark.realrepos.corpus

import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.measurement.CorpusRepository
import org.junit.jupiter.api.assertDoesNotThrow
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RealRepositoryCorpusTest {
    @Test
    fun `eligibility excludes bots formatting pure renames new only and oversized source edits`() {
        CorpusRepository("sample").use { repo ->
            repo.commit("Initial", (1..9).associate { "Source$it.kt" to "val item$it = 0\n" })
            val good = repo.commit("Correct calculation", mapOf("Source1.kt" to "val item1 = 1\n"))
            repo.commit("Bot adjustment", mapOf("Source1.kt" to "val item1 = 2\n"), "dependency[bot]")
            repo.commit("Whitespace", mapOf("Source1.kt" to "val  item1  =  2\n"))
            repo.commit("Format sources", mapOf("Source1.kt" to "val item1 = 2\n"))
            repo.commit("Rename", mapOf("Source1.kt" to null, "Renamed.kt" to "val item1 = 2\n"))
            repo.commit("Add source", mapOf("New.kt" to "val added = 1\n"))
            val deletion = repo.commit("Remove source", mapOf("New.kt" to null))
            repo.commit(
                "Broad change",
                (2..9).associate { "Source$it.kt" to "val item$it = 1\n" } +
                    ("Renamed.kt" to "val item1 = 4\n"),
            )
            val result = RealRepositoryCorpus.prepare(listOf(repo.spec()))

            assertEquals(listOf(good, deletion), result.tasks.map { it.commit })
            assertEquals(listOf("Source1.kt"), result.tasks.first().goldFiles)
            val exclusions = result.exclusions.getValue("sample")
            listOf(
                "bot",
                "whitespace-only",
                "formatting",
                "pure-rename",
                "no-parent-source-files",
                "too-many-source-files",
                "missing-parent",
            ).forEach { assertEquals(1, exclusions[it], it) }
        }
    }

    @Test
    fun `statements hide source names paths and pull request numbers`() {
        CorpusRepository("sample").use { repo ->
            repo.commit("Initial", mapOf("src/Engine.kt" to "val value = 0\n"))
            repo.commit(
                "Fix src/Engine.kt and Engine.kt (#123) for stale state",
                mapOf("src/Engine.kt" to "val value = 1\n"),
            )
            val task = RealRepositoryCorpus.prepare(listOf(repo.spec())).tasks.single()

            assertFalse(task.statement.contains("src/"))
            assertFalse(task.statement.contains("Engine"))
            assertFalse(task.statement.contains("123"))
            assertTrue(task.statement.contains("stale state"))
        }
    }

    @Test
    fun `selection uses commit time splits chronologically and preserves checkout state`() {
        CorpusRepository("sample").use { repo ->
            repo.commit("Initial", mapOf("Source.kt" to "val value = 0\n"), minute = 1)
            val late = repo.commit("Fix late case", mapOf("Source.kt" to "val value = 1\n"), minute = 30)
            repo.commit("Fix early case", mapOf("Source.kt" to "val value = 2\n"), minute = 10)
            val middle = repo.commit("Fix middle case", mapOf("Source.kt" to "val value = 3\n"), minute = 20)
            Files.createDirectories(repo.root.resolve(".kscope"))
            Files.writeString(repo.root.resolve(".kscope/preserve"), "index")
            val spec = repo.spec()
            val result = RealRepositoryCorpus.prepare(listOf(spec), limit = 2)

            assertEquals(listOf(middle, late), result.tasks.map { it.commit })
            assertEquals(listOf(RealSplit.DEV, RealSplit.TEST), result.tasks.map { it.split })
            assertEquals(result, RealRepositoryCorpus.prepare(listOf(spec), limit = 2))
            assertEquals(spec.head, repo.spec().head)
            assertEquals("index", Files.readString(repo.root.resolve(".kscope/preserve")))
        }
    }

    @Test
    fun `five verification tasks are spread across both repositories before scoring`() {
        CorpusRepository("first").use { first ->
            CorpusRepository("second").use { second ->
                listOf(first, second).forEach(::populate)
                val result = RealRepositoryCorpus.prepare(listOf(second.spec(), first.spec()))
                val verification = result.tasks.filter { it.verify }

                assertEquals(5, verification.size)
                assertEquals(setOf("first", "second"), verification.map { it.repository }.toSet())
                assertEquals(result, RealRepositoryCorpus.prepare(listOf(first.spec(), second.spec())))
                assertEquals(6, result.tasks.count { it.split == RealSplit.DEV })
            }
        }
    }

    @Test
    fun `content changing renames retain their parent path without leaking either name`() {
        CorpusRepository("sample").use { repo ->
            val original = (1..20).joinToString("\n") { "val value$it = $it" }
            repo.commit("Initial", mapOf("Old.kt" to original))
            repo.commit(
                "Correct Old.kt after moving to New.kt",
                mapOf(
                    "Old.kt" to null,
                    "New.kt" to original.replace("value20 = 20", "value20 = 21"),
                ),
            )
            val task = RealRepositoryCorpus.prepare(listOf(repo.spec())).tasks.single()

            assertEquals(listOf("Old.kt"), task.goldFiles)
            assertFalse(task.statement.contains("Old"))
            assertFalse(task.statement.contains("New"))
        }
    }

    @Test
    fun `a shallow boundary is excluded as a missing parent`() {
        CorpusRepository("sample").use { repo ->
            repo.commit("Initial", mapOf("Source.kt" to "val value = 0\n"))
            val head = repo.commit("Fix boundary", mapOf("Source.kt" to "val value = 1\n"))
            Files.writeString(repo.root.resolve(".git/shallow"), "$head\n")
            val result = RealRepositoryCorpus.prepare(listOf(repo.spec()))

            assertTrue(result.tasks.isEmpty())
            assertEquals(1, result.exclusions.getValue("sample")["missing-parent"])
            assertTrue(result.policy.contains("Verification=0/5"))
        }
    }

    @Test
    fun `commit statements stay UTF8 when Git has a different log encoding`() {
        CorpusRepository("sample").use { repo ->
            repo.commit("Initial", mapOf("Source.kt" to "val value = 0\n"))
            repo.commit("R\u00e9parer \u00e9tat", mapOf("Source.kt" to "val value = 1\n"))
            repo.git(listOf("config", "i18n.logOutputEncoding", "ISO-8859-1"))

            val result = assertDoesNotThrow { RealRepositoryCorpus.prepare(listOf(repo.spec())) }

            assertEquals("R\u00e9parer \u00e9tat", result.tasks.single().statement)
        }
    }

    private fun populate(repo: CorpusRepository) {
        repo.commit("Initial", mapOf("Source.kt" to "val value = 0\n"))
        for (value in 1..6) repo.commit("Fix case $value", mapOf("Source.kt" to "val value = $value\n"))
    }
}
