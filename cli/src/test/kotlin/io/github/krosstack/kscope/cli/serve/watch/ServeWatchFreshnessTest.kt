package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.serve.withTestServer
import io.github.krosstack.kscope.service.settings.FreshnessReading
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ServeWatchFreshnessTest {
    private fun settings(inputs: WatchServerInputs, reading: AtomicReference<FreshnessReading>, hosted: Boolean) =
        inputs.settings().let {
            it.copy(hosted = hosted, watch = it.watch.copy(freshness = { _ -> reading.get() }))
        }

    @Test
    fun `a hosted watcher follows the file and a watcher of kscope serve ignores it`() = inScannedWorkspace { root ->
        listOf(true to WatchReason.OFF, false to WatchReason.SETTLING).forEach { (hosted, expected) ->
            val inputs = WatchServerInputs()
            val reading = AtomicReference(unwatchedFreshness())
            withTestServer(root, settings(inputs, reading, hosted)) { server ->
                inputs.awaitFirstPoll()
                inputs.poll(server, 0)
                assertEquals(expected, assertNotNull(server.watchState).reason(), "hosted=$hosted")
            }
        }
    }

    @Test
    fun `a hosted watcher reports an invalid file`() = inScannedWorkspace { root ->
        val inputs = WatchServerInputs()
        val reading = AtomicReference(invalidFreshness())
        withTestServer(root, settings(inputs, reading, true)) { server ->
            inputs.awaitFirstPoll()
            inputs.poll(server, 0)
            assertEquals(WatchReason.SETTINGS, assertNotNull(server.watchState).reason())
        }
    }
}
