package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.serve.ServeClient
import io.github.krosstack.kscope.cli.serve.ServeRequest
import io.github.krosstack.kscope.cli.serve.ServeServer
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.cli.serve.runAt
import io.github.krosstack.kscope.cli.serve.withTestServer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServeWatchNoticeTest {
    @Test
    fun `each behind reason prints once after identical stdout even when quiet`() = inScannedWorkspace { root ->
        val args = listOf("symbol", "Lib", "--quiet", "--format", "json")
        val local = runAt(root, listOf("--no-server") + args)
        for (reason in WatchReason.entries) {
            val inputs = WatchServerInputs()
            withTestServer(root, inputs.settings()) { server ->
                inputs.awaitFirstPoll()
                assertEquals(0, server.query(listOf("scan", "--quiet")).exitCode)
                inputs.poll(server, 0)
                makeBehind(reason, server, inputs)
                val answer = server.query(args)
                assertEquals(local.stdout, answer.stdout, reason.name)
                assertEquals(local.statusCode, answer.exitCode, reason.name)
                assertEquals("Index is behind: ${reason.text}.\n", answer.stderr, reason.name)
            }
        }
    }

    private fun makeBehind(reason: WatchReason, server: ServeServer, inputs: WatchServerInputs) {
        val state = assertNotNull(server.watchState)
        when (reason) {
            WatchReason.SETTLING -> Unit

            WatchReason.GIT -> {
                inputs.git.set(true)
                inputs.poll(server, 0)
            }

            WatchReason.TRUSTED -> inputs.trusted.set(true)

            WatchReason.DEEPENING, WatchReason.DEEPENING_RETRY, WatchReason.DEEPENING_STOPPED ->
                leaveShallow(reason, server, inputs)

            WatchReason.RETRY -> state.updateCompleted(1, false)

            WatchReason.SETTINGS -> state.freshness.apply(invalidFreshness())

            WatchReason.OFF -> state.freshness.apply(unwatchedFreshness())

            WatchReason.STOPPED -> repeat(5) { state.updateCompleted(1, false) }
        }
    }

    private fun leaveShallow(reason: WatchReason, server: ServeServer, inputs: WatchServerInputs) = when (reason) {
        WatchReason.DEEPENING_RETRY -> inputs.leaveShallow(server, 1)
        WatchReason.DEEPENING_STOPPED -> inputs.leaveShallow(server, 5)
        else -> inputs.shallow.set(true)
    }

    @Test
    fun `up to date queries have no note and status text comes from the authenticated server`() =
        inScannedWorkspace { root ->
            val inputs = WatchServerInputs()
            withTestServer(root, inputs.settings()) { server ->
                inputs.awaitFirstPoll()
                server.query(listOf("scan", "--quiet"))
                assertEquals("", server.query(listOf("info", "--quiet")).stderr)
                val current = runAt(root, listOf("serve", "--status"))
                assertEquals(0, current.statusCode)
                assertTrue(current.stdout.contains("Watcher: up to date"))
                assertTrue(current.stdout.contains("last update"))
                assertTrue(current.stdout.contains("0 automatic updates"))
                inputs.poll(server, 0)
                val behind = runAt(root, listOf("serve", "--status"))
                assertTrue(behind.stdout.contains("Watcher: behind"))
                repeat(5) { assertNotNull(server.watchState).updateCompleted(1, false) }
                val stopped = runAt(root, listOf("serve", "--status"))
                assertTrue(stopped.stdout.contains("Watcher: stopped after failures"))
                for (text in listOf(current.stdout, behind.stdout, stopped.stdout)) {
                    assertFalse(text.contains(server.metadata.token))
                }
                val bad = ServeRequest(token = "wrong", action = "status")
                assertEquals(null, ServeClient.exchange(server.metadata, bad) { error("unauthenticated output") })
            }
        }

    @Test
    fun `throwing automatic scans stop after five and report failure without scan output`() =
        inScannedWorkspace { root ->
            val inputs = WatchServerInputs()
            val attempts = List(5) { CountDownLatch(1) }
            val failures = AtomicInteger()
            val scan = ScanRunner { _, _, _, _ ->
                attempts[failures.getAndIncrement()].countDown()
                error("automatic failure")
            }
            withTestServer(root, inputs.settings(), scan) { server ->
                inputs.awaitFirstPoll()
                for ((attempt, second) in listOf(4L, 14L, 34L, 74L, 154L).withIndex()) {
                    inputs.poll(server, second, "initial")
                    assertTrue(attempts[attempt].await(5, TimeUnit.SECONDS))
                    val query = server.query(listOf("info", "--quiet"))
                    assertEquals(0, query.exitCode)
                    assertFalse(query.stderr.contains("automatic failure"))
                }
                assertEquals(5, failures.get())
                assertEquals(WatchReason.STOPPED, assertNotNull(server.watchState).reason())
            }
        }
}
