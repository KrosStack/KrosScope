package io.github.krosstack.kscope.cli.serve.map

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.cli.serve.withTestServer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServeMapScanTest {
    @Test
    fun `a viewer is told while a scan runs and again when it is over`() = inScannedWorkspace { root ->
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val runner = ScanRunner { workspace, options, rebuild, verify ->
            started.countDown()
            check(release.await(30, TimeUnit.SECONDS))
            ScanRunner.DEFAULT.run(workspace, options, rebuild, verify)
        }
        withTestServer(root, ServeSettings(mapEnabled = true), runner) { server ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                MapAddress(checkNotNull(server.mapUrl)).events().use { stream ->
                    stream.untilIndexed()
                    val scan = executor.submit<Int> { server.query(listOf("scan")).exitCode }
                    try {
                        generateSequence { stream.nextState() }.first { it.flag("scanning") }
                        assertTrue(started.await(30, TimeUnit.SECONDS))
                    } finally {
                        release.countDown()
                    }

                    generateSequence { stream.nextState() }.first { !it.flag("scanning") }

                    assertEquals(0, scan.get(30, TimeUnit.SECONDS))
                }
            } finally {
                executor.shutdownNow()
            }
        }
    }
}
