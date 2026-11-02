package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.serve.ServeServer
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.serve.ServerAlreadyRunning
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import io.github.krosstack.kscope.cli.service.inServiceHome
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostedWorkspaceRetryTest {
    private val clock = AtomicLong()
    private val events = ServiceEvents()

    private fun seconds(count: Long) = TimeUnit.SECONDS.toNanos(count)

    private fun workspace(root: Path, failures: List<Throwable>, serving: Boolean = false): HostedWorkspace {
        val remaining = ArrayDeque(failures)
        val opening = { path: Path, serve: ServeSettings, scan: ScanRunner ->
            remaining.removeFirstOrNull()?.let { throw it }
            check(serving) { "the workspace was opened although it should fail" }
            ServeServer.open(path, serve, scan)
        }
        val settings = ServiceSettings(
            serve = ServeSettings(hosted = true),
            clock = clock::get,
            onChange = events,
            opening = opening,
        )
        return HostedWorkspace(WorkspacePaths.real(root), settings) {}
    }

    private fun HostedWorkspace.failAndAdvance(): Long {
        open()
        val wait = checkNotNull(waitMillis(clock.get()))
        clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(wait))
        return wait
    }

    @Test
    fun `the wait doubles with each consecutive failure up to ten minutes`() = inScannedWorkspace { root ->
        val failures = List(7) { IllegalStateException("broken") }
        val hosted = workspace(root, failures)

        val waits = failures.map { hosted.failAndAdvance() }

        val expected = listOf(30L, 60L, 120L, 240L, 480L, 600L, 600L).map { TimeUnit.SECONDS.toMillis(it) }
        assertEquals(expected, waits)
    }

    @Test
    fun `a workspace is due when its wait has passed and not a moment before`() = inScannedWorkspace { root ->
        val hosted = workspace(root, listOf(IllegalStateException("one"), IllegalStateException("two")))
        hosted.failAndAdvance()
        hosted.open()
        val failedAt = clock.get()

        assertFalse(hosted.due(failedAt + seconds(60) - 1))
        assertTrue(hosted.due(failedAt + seconds(60)))
    }

    @Test
    fun `a workspace that waits for another server keeps the wait of 30 seconds and does not count as failed`() =
        inScannedWorkspace { root ->
            val busy = ServerAlreadyRunning()
            val broken = IllegalStateException("broken")
            val hosted = workspace(root, listOf(busy, busy, broken, busy, broken))

            val waits = (1..5).map { hosted.failAndAdvance() }

            assertEquals(listOf(30L, 30L, 30L, 30L, 60L).map { TimeUnit.SECONDS.toMillis(it) }, waits)
        }

    @Test
    fun `a retry at once makes a workspace that is not served due`() = inScannedWorkspace { root ->
        val hosted = workspace(root, listOf(IllegalStateException("broken")))
        hosted.open()
        assertFalse(hosted.due(clock.get()))

        hosted.retryAtOnce()

        assertTrue(hosted.due(clock.get()))
        assertNull(hosted.waitMillis(clock.get()))
    }

    @Test
    fun `the wait returns to 30 seconds once the workspace was served`() = inScannedWorkspace { root ->
        val failures = listOf(IllegalStateException("one"), IllegalStateException("two"))
        val hosted = workspace(root, failures, serving = true)
        hosted.failAndAdvance()
        assertEquals(TimeUnit.SECONDS.toMillis(60), hosted.failAndAdvance())

        val path = WorkspacePaths.real(root)

        hosted.open()
        val served = events.await(path) { it is WorkspaceState.Served } as WorkspaceState.Served
        hosted.retryAtOnce()
        served.server.requestStop()
        events.await(path) { it == WorkspaceState.NotServed("the server ended") }

        assertEquals(TimeUnit.SECONDS.toMillis(30), hosted.waitMillis(clock.get()))
        hosted.stop()
    }

    @Test
    fun `a reload of the running service tries a workspace that is not served at once`() = inScannedWorkspace { root ->
        inServiceHome { home ->
            val calls = AtomicInteger()
            val opening = { path: Path, serve: ServeSettings, scan: ScanRunner ->
                check(calls.incrementAndGet() > 1) { "broken" }
                ServeServer.open(path, serve, scan)
            }
            val settings = ServiceSettings(ServeSettings(watch = quietWatch()), opening = opening)
            withTestService(home, listOf(root), settings) { service ->
                service.awaitState(root) { it == WorkspaceState.NotServed("broken") }

                val answer = service.control("reload")

                assertEquals(0, answer.exitCode, answer.stderr)
                service.awaitServed(root)
                assertEquals(2, calls.get())
            }
        }
    }
}
