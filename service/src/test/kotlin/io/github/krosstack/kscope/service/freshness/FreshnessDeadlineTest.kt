package io.github.krosstack.kscope.service.freshness

import io.github.krosstack.kscope.service.TemporaryTree
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FreshnessDeadlineTest {
    private val base: Path = Files.createTempDirectory("kscope-freshness-deadline").toRealPath()
    private val scan: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val times = ScanTimes(1, scan.toString(), scan.toString())
    private val commit = "a".repeat(40)

    @AfterTest
    fun cleanUp() {
        TemporaryTree.deleteEventually(base)
    }

    private fun steppingDeadline(allowedChecks: Int): Deadline {
        var nanos = 0L
        return Deadline(allowedChecks.toLong()) { nanos.also { nanos += MILLISECOND } }
    }

    private fun plainFolder(): Path {
        val root = base.resolve("plain")
        listOf("a", "b", "c", "d").forEach { directory ->
            val file = FreshnessFixtures.write(root, "$directory/File.txt", "text\n")
            Files.setLastModifiedTime(file, FileTime.from(scan.plusSeconds(60)))
        }
        return root
    }

    private fun packedRefs(lines: Int): Path {
        val root = base.resolve("packed")
        FreshnessFixtures.write(root, ".git/HEAD", "ref: refs/heads/main\n")
        val refs = (1..lines).joinToString("\n") { "${"b".repeat(40)} refs/heads/branch-$it" }
        FreshnessFixtures.write(root, ".git/packed-refs", "$refs\n$commit refs/heads/main\n")
        return root
    }

    @Test
    fun `a walk of a folder that Git does not track that is stopped by the limit counts nothing`() {
        val root = plainFolder()
        val checkout = Checkout(root, null, tree = true)

        val whole = CheckoutQuestion(checkout, times, 300, Deadline(60_000)).answer(unusedGit())
        val stopped = CheckoutQuestion(checkout, times, 300, steppingDeadline(3)).answer(unusedGit())

        assertEquals(4, whole.changedFiles)
        assertEquals(FreshnessState.BEHIND, whole.tree)
        assertEquals(0, stopped.changedFiles)
        assertEquals(FreshnessState.NOT_CHECKED, stopped.tree)
        assertEquals(false, stopped.estimate)
    }

    @Test
    fun `reading a large list of packed references is stopped by the limit and the commit is not checked`() {
        val root = packedRefs(LINES)
        val checkout = Checkout(root, commit, tree = false)

        val whole = CheckoutQuestion(checkout, null, 300, Deadline(60_000)).fromFiles()
        val stopped = CheckoutQuestion(checkout, null, 300, steppingDeadline(3)).fromFiles()

        assertEquals(FreshnessState.NOT_BEHIND, whole.commit)
        assertEquals(FreshnessState.NOT_CHECKED, stopped.commit)
    }

    private fun unusedGit() = GitChanges("kscope-no-such-git", StartedProcesses())

    private companion object {
        const val MILLISECOND = 1_000_000L
        const val LINES = 50
    }
}
