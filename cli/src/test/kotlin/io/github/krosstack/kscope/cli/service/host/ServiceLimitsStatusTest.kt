package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.service.entry.SessionLine
import io.github.krosstack.kscope.cli.service.home.ServiceLimits
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import io.github.krosstack.kscope.cli.service.inServiceHome
import io.github.krosstack.kscope.cli.service.runService
import io.github.krosstack.kscope.cli.service.stdoutLines
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ServiceLimitsStatusTest {
    private val clock = AtomicLong()

    private fun statusAfterFirstLine(home: Path) = runService(home, "status").stdoutLines().drop(1)

    private fun settings() = ServiceSettings(
        ServeSettings(clock = clock::get, watch = quietWatch()),
        heap = FakeHeap(),
        lookMillis = Long.MAX_VALUE,
    )

    @Test
    fun `status prints the limits on the line after the first one, and none where a limit is not set`() =
        inServiceHome { home ->
            val expected = mapOf(
                ServiceLimits() to "Limits: idle 30 minutes, memory none.",
                ServiceLimits(idleMinutes = 5, memoryMib = 512) to "Limits: idle 5 minutes, memory 512 MiB.",
                ServiceLimits(idleMinutes = 0, memoryMib = 0) to "Limits: idle none, memory none.",
            )
            val noWorkspace = "It keeps no workspace. Add one with `kscope service add`."
            val notRegistered = SessionLine.NOT_REGISTERED
            for ((limits, line) in expected) {
                withTestService(home, emptyList(), settings(), limits) {
                    val status = runService(home, "status")

                    assertEquals(0, status.statusCode, status.output)
                    assertEquals(listOf(line, notRegistered, noWorkspace), status.stdoutLines().drop(1))
                }
            }
        }

    @Test
    fun `status says that a released workspace is served but not in memory, and says served again after a query`() =
        inScannedWorkspace { root ->
            inServiceHome { home ->
                withTestService(home, listOf(root), settings()) { service ->
                    val server = service.awaitServed(root)
                    val path = WorkspacePaths.real(root)

                    server.releases.request()
                    service.awaitReleased(root)
                    val released = statusAfterFirstLine(home).last()

                    assertEquals(
                        "$path: served, not in memory. ${server.watchState?.status()} Index level: resolved.",
                        released,
                    )
                    assertEquals(0, runKscope("info", "--workspace", root.toString()).statusCode)
                    assertEquals(
                        "$path: served. ${server.watchState?.status()} Index level: resolved.",
                        statusAfterFirstLine(home).last(),
                    )
                }
            }
        }

    @Test
    fun `a reload applies a changed idle limit to the running service`() = inScannedWorkspace { root ->
        inServiceHome { home ->
            withTestService(home, listOf(root), settings(), ServiceLimits(idleMinutes = 10)) { service ->
                service.awaitServed(root)
                clock.set(TimeUnit.MINUTES.toNanos(6))
                service.directory.file.write(listOf(WorkspacePaths.key(root)), ServiceLimits(idleMinutes = 5))

                val answer = service.control("reload")

                assertEquals(0, answer.exitCode, answer.stderr)
                assertEquals("Limits: idle 5 minutes, memory none.", statusAfterFirstLine(home).first())
                service.instance.look()
                assertFalse(service.awaitReleased(root).loaded)
            }
        }
    }

    @Test
    fun `a reload of a file without the keys goes back to 30 minutes and no ceiling`() = inScannedWorkspace { root ->
        inServiceHome { home ->
            val limits = ServiceLimits(idleMinutes = 5, memoryMib = 256)
            withTestService(home, listOf(root), settings(), limits) { service ->
                service.awaitServed(root)
                service.directory.file.write(listOf(WorkspacePaths.key(root)))

                val answer = service.control("reload")

                assertEquals(0, answer.exitCode, answer.stderr)
                assertEquals("Limits: idle 30 minutes, memory none.", statusAfterFirstLine(home).first())
            }
        }
    }
}
