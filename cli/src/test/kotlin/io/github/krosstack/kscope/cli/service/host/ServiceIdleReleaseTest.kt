package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.serve.ServeServer
import io.github.krosstack.kscope.cli.serve.watch.WatchServerInputs
import io.github.krosstack.kscope.cli.service.home.ServiceLimits
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import io.github.krosstack.kscope.cli.service.inServiceHome
import io.github.krosstack.kscope.service.DiagnosticSummary
import io.github.krosstack.kscope.service.ScanResult
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceIdleReleaseTest {
    private val unchanged = ScanResult(0, 0, 0, 0, DiagnosticSummary(0, 0, 0, emptyList()), null, emptyMap(), "")
    private val inputs = WatchServerInputs()
    private val scans = AtomicInteger()

    private fun settings() = ServiceSettings(
        serve = inputs.settings(),
        scanRunner = ScanRunner { _, _, _, _ ->
            scans.incrementAndGet()
            unchanged
        },
        lookMillis = Long.MAX_VALUE,
    )

    private fun at(seconds: Long) = inputs.clock.set(TimeUnit.SECONDS.toNanos(seconds))

    private fun info(root: Path) = runKscope("info", "--workspace", root.toString())

    private fun automaticUpdate(server: ServeServer, startSeconds: Long, stamp: String, updates: Int) {
        inputs.poll(server, startSeconds, stamp)
        inputs.poll(server, startSeconds + QUIET_SECONDS, stamp)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (!checkNotNull(server.watchState).status().contains("; $updates automatic updates.")) {
            check(System.nanoTime() < deadline) { "the automatic update never ended: ${server.watchState?.status()}" }
            Thread.onSpinWait()
        }
        assertEquals(updates, scans.get())
    }

    @Test
    fun `an idle workspace is released, automatic updates neither postpone nor undo it, and a query loads it again`() =
        inScannedWorkspace { root ->
            inServiceHome { home ->
                withTestService(home, listOf(root), settings(), ServiceLimits(idleMinutes = 10)) { service ->
                    inputs.awaitFirstPoll()
                    val server = service.awaitServed(root)
                    at(60)
                    val before = info(root)
                    assertEquals(0, before.statusCode, before.output)

                    at(600)
                    service.instance.look()
                    info(root)
                    assertTrue(service.events.history(WorkspacePaths.real(root)).none { it.contains("not in memory") })

                    automaticUpdate(server, startSeconds = 1_140, stamp = "first", updates = 1)
                    at(1_260)
                    service.instance.look()
                    service.awaitReleased(root)

                    automaticUpdate(server, startSeconds = 1_320, stamp = "second", updates = 2)
                    assertFalse(server.loaded)
                    val after = info(root)

                    assertEquals(before.stdout, after.stdout)
                    assertEquals(before.statusCode, after.statusCode)
                    assertTrue(server.loaded)
                    assertTrue(service.events.history(WorkspacePaths.real(root)).last().startsWith("served. "))
                }
            }
        }

    private companion object {
        const val WAIT_SECONDS = 60L
        const val QUIET_SECONDS = 4L
    }
}
