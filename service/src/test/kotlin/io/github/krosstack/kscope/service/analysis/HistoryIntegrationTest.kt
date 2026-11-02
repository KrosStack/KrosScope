package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.model.HistorySignals
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Scans a sample that is a Git repository with three commits and reads the history signals back. */
class HistoryIntegrationTest {
    private val sample = SampleWorkspace("tests")
    private val greeter = "core/src/main/kotlin/sample/tests/Greeter.kt"
    private val formatter = "core/src/main/kotlin/sample/tests/Formatter.kt"
    private val report = "other/src/main/kotlin/sample/other/Report.kt"
    private var minutes = 0L

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    private fun git(vararg arguments: String) {
        val date = Instant.parse("2024-02-01T00:00:00Z").plusSeconds(++minutes * SECONDS_PER_MINUTE).toString()
        TestGit.run(sample.root, arguments.toList(), identity(date))
    }

    private fun identity(date: String) = mapOf(
        "GIT_AUTHOR_NAME" to "dev",
        "GIT_AUTHOR_EMAIL" to "dev@example.invalid",
        "GIT_AUTHOR_DATE" to date,
        "GIT_COMMITTER_NAME" to "dev",
        "GIT_COMMITTER_EMAIL" to "dev@example.invalid",
        "GIT_COMMITTER_DATE" to date,
    )

    private fun commitEdit(round: Int) {
        sample.write(greeter, "package sample.tests\n\nclass Greeter { val round$round = $round }\n")
        sample.write(formatter, "package sample.tests\n\nclass Formatter { val round$round = $round }\n")
        git("add", "-A")
        git("commit", "-q", "-m", "edit $round")
    }

    private fun repositoryWithHistory() {
        assumeTrue(TestGit.available(), "Git is not available")
        sample.write(".gitignore", ".kscope/" + System.lineSeparator())
        git("init", "-q")
        git("add", "-A")
        git("commit", "-q", "-m", "initial")
        repeat(2) { commitEdit(it) }
    }

    private fun KrosScope.metric(name: String, path: String): Double? = graph().metrics(name)
        .singleOrNull { (it.node as? NodeRef.OfArtifact)?.ref?.path == RepoPath(path) }?.value

    @Test
    fun `a scan of a repository with history stores metrics for its files`() {
        repositoryWithHistory()
        KrosScope.open(sample.root).use { service ->
            val scan = service.scan()
            assertEquals(0, scan.diagnostics.errors, "${scan.diagnostics.entries}")
            assertTrue(scan.diagnostics.entries.none { it.code == "history.not-available" })
            assertEquals(3.0, service.metric(HistorySignals.COMMITS, greeter))
            assertEquals(1.0, service.metric(HistorySignals.AUTHORS, greeter))
            assertEquals(0.0, service.metric(HistorySignals.RECENCY_RANK, greeter))
            assertEquals(1.0, service.metric(HistorySignals.COMMITS, report))
            assertEquals(2.0, service.metric(HistorySignals.RECENCY_RANK, report))
        }
    }

    @Test
    fun `files that changed together are related and info counts the metrics by name`() {
        repositoryWithHistory()
        KrosScope.open(sample.root).use { service ->
            val scan = service.scan()
            val related = service.graph().relationships(RelationshipQuery(setOf(RelationKind.CO_CHANGES_WITH)))
            assertEquals(2, related.size, "$related")
            assertEquals(setOf("3"), related.map { it.attributes[HistorySignals.SUPPORT] }.toSet())
            assertEquals(setOf("1.0000"), related.map { it.attributes[HistorySignals.CONFIDENCE] }.toSet())
            val metrics = service.info().metricsByName
            val names = setOf(HistorySignals.AUTHORS, HistorySignals.COMMITS, HistorySignals.RECENCY_RANK)
            assertEquals(names, metrics.keys)
            assertEquals(scan.artifacts.toLong(), metrics.getValue(HistorySignals.COMMITS))
        }
    }

    @Test
    fun `a new commit refreshes the signals and an unchanged scan leaves them alone`() {
        repositoryWithHistory()
        KrosScope.open(sample.root).use { service ->
            service.scan()
            assertEquals(0, service.scan().unitsProduced)
            commitEdit(2)
            service.scan()
            assertEquals(4.0, service.metric(HistorySignals.COMMITS, greeter))
            assertEquals(true, service.scan(verify = true).verification?.matches)
        }
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}
