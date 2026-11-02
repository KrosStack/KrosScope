package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.serve.ServeOutput
import io.github.krosstack.kscope.cli.serve.ServeRequest
import io.github.krosstack.kscope.cli.serve.ServeRequests
import io.github.krosstack.kscope.service.KrosScope
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchScanBaselineTest {
    @Test
    fun `an edit during an automatic scan needs exactly one further update`() {
        val clock = WatchTestClock()
        clock.ready()
        clock.change("during")
        clock.state.updateCompleted(0, false)
        clock.at(5)
        clock.state.poll()
        clock.at(8)
        clock.state.poll()
        assertFalse(clock.state.beginUpdate())
        clock.at(9)
        clock.state.poll()
        assertTrue(clock.state.beginUpdate())
        clock.state.updateCompleted(0, false)
        clock.at(20)
        clock.state.poll()
        assertFalse(clock.state.beginUpdate())
    }

    @Test
    fun `an unchanged stamp during an automatic scan needs no further update`() {
        val clock = WatchTestClock()
        clock.ready()
        clock.state.updateCompleted(0, false)
        clock.at(20)
        clock.state.poll()
        assertFalse(clock.state.beginUpdate())
    }

    @Test
    fun `an edit during a requested scan needs exactly one further update`() = requestedScan(changes = true)

    @Test
    fun `an unchanged stamp during a requested scan needs no further update`() = requestedScan(changes = false)

    @Test
    fun `a failed pre scan stamp leaves a requested scan behind until another update`() = inScannedWorkspace { root ->
        val clock = WatchTestClock()
        var available = false
        val state = WatchState(WatchSettings(), { clock.nanos }, {
            check(available) { "listing unavailable" }
            clock.stamp()
        }, { false })
        ServeRequests(root, {}, ScanRunner.DEFAULT, {}).use { requests ->
            requests.watch = state
            val request = ServeRequest(arguments = listOf("scan", "--quiet"), directory = root.toString())
            assertEquals(0, requests.execute(request, ServeOutput(StringWriter().buffered())))
            assertEquals(WatchReason.RETRY, state.reason())
            available = true
            clock.at(9)
            state.poll()
            assertEquals(0, clock.reads)
            clock.at(10)
            state.poll()
            clock.at(14)
            state.poll()
            assertTrue(state.beginUpdate())
            state.updateCompleted(0, false)
            assertEquals(null, state.reason())
        }
    }

    private fun requestedScan(changes: Boolean) = inScannedWorkspace { root ->
        val clock = WatchTestClock()
        val runner = ScanRunner { workspace, options, _, _ ->
            KrosScope.open(workspace, options).use { it.scan() }.also {
                if (changes) clock.change("during")
            }
        }
        ServeRequests(root, {}, runner, {}).use { requests ->
            requests.watch = clock.state
            val request = ServeRequest(arguments = listOf("scan", "--quiet"), directory = root.toString())
            assertEquals(0, requests.execute(request, ServeOutput(StringWriter().buffered())))
            clock.at(1)
            clock.state.poll()
            clock.at(3)
            clock.state.poll()
            assertFalse(clock.state.beginUpdate())
            clock.at(4)
            clock.state.poll()
            assertEquals(changes, clock.state.beginUpdate())
            if (changes) clock.state.updateCompleted(0, false)
            clock.at(20)
            clock.state.poll()
            assertFalse(clock.state.beginUpdate())
        }
    }
}
