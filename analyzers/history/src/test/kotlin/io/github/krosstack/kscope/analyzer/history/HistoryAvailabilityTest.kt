package io.github.krosstack.kscope.analyzer.history

import io.github.krosstack.kscope.model.DiagnosticSeverity
import java.nio.file.Files
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryAvailabilityTest {
    private fun Emitted.assertUnavailable(reasonPart: String) {
        val diagnostic = diagnostics.single()
        assertEquals("history.not-available", diagnostic.code.value)
        assertEquals(DiagnosticSeverity.INFO, diagnostic.severity)
        assertTrue(reasonPart in diagnostic.message, diagnostic.message)
        assertEquals(1, facts.size)
    }

    @Test
    fun `a directory that is not in a work tree gets one informational diagnostic and no facts`() {
        assumeGit()
        val directory = Files.createTempDirectory("kscope-no-git")
        try {
            produceHistory(listOf("Core.kt"), directory, head = "abc123").assertUnavailable("work tree")
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a repository with one commit has no history to speak of`() = SyntheticRepository().use { repository ->
        repository.commit("alice", "Core.kt" to "1")
        repository.produce().assertUnavailable("fewer than 2 commits")
    }

    @Test
    fun `a repository without commits is not available either`() = SyntheticRepository().use { repository ->
        repository.write("Core.kt", "1")
        produceHistory(repository.files(), repository.directory, head = null).assertUnavailable("no commit")
    }

    @Test
    fun `a shallow clone with a single commit is not available`() = SyntheticRepository().use { origin ->
        repeat(3) { origin.commit("alice", "Core.kt" to "$it", "Util.kt" to "$it") }
        SyntheticRepository(Files.createTempDirectory("kscope-clone"), initialize = false).use { clone ->
            origin.git("clone", "-q", "--depth", "1", origin.directory.toUri().toString(), clone.directory.toString())
            clone.produce().assertUnavailable("fewer than 2 commits")
        }
    }

    @Test
    fun `a missing git executable is reported as not available`() = SyntheticRepository().use { repository ->
        repeat(2) { repository.commit("alice", "Core.kt" to "$it") }
        val producer = GitHistoryProducer(GitProcess("git-that-does-not-exist-kscope", Duration.ofSeconds(5)))
        produceHistory(repository.files(), repository.directory, repository.head(), producer)
            .assertUnavailable("could not be started")
    }
}
