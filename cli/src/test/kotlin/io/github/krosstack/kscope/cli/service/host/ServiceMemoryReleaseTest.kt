package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.service.home.ServiceLimits
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import io.github.krosstack.kscope.cli.service.inServiceHome
import io.github.krosstack.kscope.cli.service.inTwoScannedWorkspaces
import io.github.krosstack.kscope.cli.service.runService
import io.github.krosstack.kscope.cli.service.workspaceLines
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceMemoryReleaseTest {
    private val clock = AtomicLong()
    private val heap = FakeHeap(used = 90 * MIB, collections = 1)
    private val limits = ServiceLimits(idleMinutes = 0, memoryMib = 100)

    private fun TestService.neverReleased(root: Path) =
        events.history(WorkspacePaths.real(root)).none { it.contains("not in memory") }

    private fun requestAt(seconds: Long, root: Path) {
        clock.set(TimeUnit.SECONDS.toNanos(seconds))
        assertEquals(0, runKscope("info", "--workspace", root.toString()).statusCode)
    }

    @Test
    fun `above the ceiling the service releases the workspace used least recently and then one per collection`() =
        inTwoScannedWorkspaces { older, newer ->
            inServiceHome { home ->
                val serve = ServeSettings(clock = clock::get, watch = quietWatch())
                val settings = ServiceSettings(serve, heap = heap, lookMillis = Long.MAX_VALUE)
                withTestService(home, listOf(older, newer), settings, limits) { service ->
                    service.awaitServed(older)
                    service.awaitServed(newer)
                    requestAt(1, older)
                    requestAt(2, newer)

                    service.instance.look()
                    service.awaitReleased(older)
                    requestAt(3, newer)
                    service.instance.look()
                    requestAt(4, newer)

                    assertTrue(service.neverReleased(newer))
                    heap.collections = 2
                    service.instance.look()
                    service.awaitReleased(newer)
                    val workspaces = runService(home, "status").workspaceLines()
                    assertEquals(listOf(true, true), workspaces.map { it.contains(": served, not in memory. ") })
                }
            }
        }
}
