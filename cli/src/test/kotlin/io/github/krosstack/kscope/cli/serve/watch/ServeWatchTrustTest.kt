package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.cli.serve.withTestServer
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.indexRequiresTrust
import java.nio.file.Path
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServeWatchTrustTest {
    @Test
    fun `a stored trusted provider prevents automatic scans until an untrusted requested scan`() =
        inScannedWorkspace { root ->
            val inputs = WatchServerInputs()
            val automatic = CountDownLatch(1)
            val scans = AtomicInteger()
            val runner = ScanRunner { workspace, options, rebuild, verify ->
                val result = KrosScope.open(workspace, options.copy(trustBuild = false)).use {
                    it.scan(rebuild, verify)
                }
                scans.incrementAndGet()
                if (options.trustBuild) markTrustedProvider(workspace)
                if (scans.get() == 3) automatic.countDown()
                result
            }
            val watch = inputs.settings().watch.copy(requiresTrust = { it.indexRequiresTrust() })
            withTestServer(root, inputs.settings().copy(watch = watch), runner) { server ->
                inputs.awaitFirstPoll()
                assertEquals(0, server.query(listOf("scan", "--trust-build", "--quiet")).exitCode)
                inputs.poll(server, 0)
                inputs.poll(server, 10)
                val paused = server.query(listOf("symbol", "Lib", "--quiet"))
                assertTrue(paused.stderr.contains("level exact"))
                assertEquals(1, scans.get())
                assertEquals(0, server.query(listOf("scan", "--level", "resolved", "--rebuild", "--quiet")).exitCode)
                assertEquals("", server.query(listOf("info", "--quiet")).stderr)
                inputs.poll(server, 10, "next")
                inputs.poll(server, 14, "next")
                assertTrue(automatic.await(10, TimeUnit.SECONDS))
                assertEquals(0, server.query(listOf("info", "--quiet")).exitCode)
                assertEquals(3, scans.get())
            }
        }

    private fun markTrustedProvider(workspace: Path) {
        DriverManager.getConnection("jdbc:sqlite:${workspace.resolve(".kscope/index.db")}").use { connection ->
            connection.createStatement().use {
                assertEquals(
                    1,
                    it.executeUpdate(
                        "UPDATE producer SET name = 'kscope.build.gradle.tooling' " +
                            "WHERE name = 'kscope.build.gradle.static'",
                    ),
                )
            }
        }
    }
}
