package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.spi.worker.WorkerProtocol
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WorkerStartupTest {
    private val worker = FakeWorkerHarness()

    @AfterTest
    fun cleanUp() {
        worker.close()
    }

    @Test
    fun `a worker with another protocol version is unavailable and is destroyed`() {
        val version = WorkerProtocol.PROTOCOL_VERSION
        assertContains(worker.unavailable("old-protocol"), "it speaks protocol ${version + 1}, this client $version")
        worker.assertProcessEnded()
    }

    @Test
    fun `a worker of protocol three is unavailable and is destroyed`() {
        val version = WorkerProtocol.PROTOCOL_VERSION
        assertContains(worker.unavailable("protocol-three"), "it speaks protocol 3, this client $version")
        worker.assertProcessEnded()
    }

    @Test
    fun `a worker that exits while starting is unavailable and its last output is reported`() {
        val reason = worker.unavailable("crash-start")
        assertContains(reason, "it exited before it answered the handshake")
        assertContains(reason, "boom: the worker cannot start")
    }

    @Test
    fun `a worker that does not answer the handshake in time is unavailable and is destroyed`() {
        val reason = worker.unavailable("silent", WorkerTimeouts(1.seconds, 1.minutes, 10.seconds))
        assertContains(reason, "it did not answer the handshake within 1s")
        worker.assertProcessEnded()
    }

    @Test
    fun `a command that cannot run is unavailable`() {
        val launcher = WorkerLauncher({ WorkerCommand.Runnable(listOf("kscope-no-such-executable")) })
        assertContains(assertIs<WorkerStart.Unavailable>(launcher.start {}).reason, "could not be started")
    }

    @Test
    fun `a missing worker is unavailable for the reason the locator gives`() {
        val launcher = WorkerLauncher({ WorkerCommand.Missing("it is not installed") })
        assertEquals("it is not installed", assertIs<WorkerStart.Unavailable>(launcher.start {}).reason)
    }
}
