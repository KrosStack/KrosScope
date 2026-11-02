package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.analyzer.kotlin.process.ModuleSamples.module
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.worker.ScriptOutline
import io.github.krosstack.kscope.spi.worker.WorkerFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WorkerRequestTest {
    private val worker = FakeWorkerHarness()

    @AfterTest
    fun cleanUp() {
        worker.close()
    }

    @Test
    fun `batches reach the callback in the order the worker sends them`() {
        val process = worker.started("ok")
        val (outcome, received) = worker.analyse(process, module("one", "a", "b"), module("two", "c"))
        assertEquals(WorkerOutcome.Completed, outcome)
        assertEquals(listOf("a", "b", "c"), received.map { it.owner.key.substringAfterLast('@') })
    }

    @Test
    fun `a worker answers several requests one after the other`() {
        val process = worker.started("ok")
        assertEquals(WorkerOutcome.Completed, worker.analyse(process, module("one", "a")).first)
        assertEquals(WorkerOutcome.Completed, worker.analyse(process, module("two", "b")).first)
    }

    @Test
    fun `scripts are outlined`() {
        val process = worker.started("ok")
        val ref = ArtifactRef(RepositoryId("repo"), RepoPath("build.gradle.kts"))
        val outlines = mutableMapOf<ArtifactRef, ScriptOutline>()
        val script = WorkerFile(ref, "/workspace/build.gradle.kts", ContentHash("hash"))
        assertEquals(WorkerOutcome.Completed, process.outline(listOf(script)) { r, o -> outlines[r] = o })
        assertEquals(listOf("plugins"), outlines.getValue(ref).calls.map { it.name })
    }

    @Test
    fun `batches that arrived before a failure are kept and the failure names the reason of the worker`() {
        val (outcome, received) = worker.analyse(worker.started("fail"), module("one", "a", "b"))
        assertContains(assertIs<WorkerOutcome.Failed>(outcome).message, "worker.analysis-failed: kaput")
        assertEquals(1, received.size)
    }

    @Test
    fun `a worker that exits in the middle of a request fails it with the exit code`() {
        val (outcome, _) = worker.analyse(worker.started("exit"), module("one", "a", "b"))
        assertContains(assertIs<WorkerOutcome.Failed>(outcome).message, "exited with exit code 7")
    }

    @Test
    fun `lines on standard output that are no messages go to the diagnostics`() {
        val (outcome, _) = worker.analyse(worker.started("noise"), module("one", "a"))
        assertEquals(WorkerOutcome.Completed, outcome)
        assertContains(worker.diagnostics, "a line that is not a protocol message")
    }

    @Test
    fun `standard error is forwarded line by line`() {
        val (outcome, _) = worker.analyse(worker.started("stderr"), module("one", "a"))
        assertEquals(WorkerOutcome.Completed, outcome)
        assertTrue(worker.awaitDiagnostic("fake diagnostic line"), "The error output did not arrive")
    }
}
