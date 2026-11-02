package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.inScannedWorkspace
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServeReleaseRequestTest {
    private val info = listOf("info", "--format", "json")

    @Test
    fun `a release requested from outside is performed before the next request, which is answered as before`() =
        inScannedWorkspace { root ->
            val changes = LinkedBlockingQueue<Boolean>()
            val settings = ServeSettings(hosted = true, onLoadChange = { changes.add(it) })
            withTestServer(root, settings) { server ->
                val before = server.query(info)
                assertTrue(server.loaded)

                server.releases.request()
                val after = server.query(info)

                assertEquals(before, after)
                assertEquals(listOf(false, true), changes.toList())
                assertTrue(server.loaded)
            }
        }

    @Test
    fun `a server that is not hosted ignores a request to release`() = inScannedWorkspace { root ->
        val changes = LinkedBlockingQueue<Boolean>()
        withTestServer(root, ServeSettings(onLoadChange = { changes.add(it) })) { server ->
            val before = server.query(info)

            server.releases.request()
            val after = server.query(info)

            assertEquals(before, after)
            assertEquals(emptyList(), changes.toList())
            assertTrue(server.loaded)
        }
    }

    @Test
    fun `a release requested during a request waits until that request has ended`() = inScannedWorkspace { root ->
        val blocking = AtomicBoolean()
        val running = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val released = CountDownLatch(1)
        val settings = ServeSettings(
            hosted = true,
            onRequest = {
                if (blocking.get()) {
                    running.countDown()
                    check(proceed.await(WAIT_SECONDS, TimeUnit.SECONDS))
                }
            },
            onLoadChange = { loaded -> if (!loaded) released.countDown() },
        )
        withTestServer(root, settings) { server ->
            val before = server.query(info)
            val executor = Executors.newSingleThreadExecutor()
            try {
                blocking.set(true)
                val answer = executor.submit<ServeAnswer> { server.query(info) }
                assertTrue(running.await(WAIT_SECONDS, TimeUnit.SECONDS))

                server.releases.request()

                assertTrue(server.loaded)
                assertEquals(1L, released.count)
                proceed.countDown()
                assertEquals(before, answer.get(WAIT_SECONDS, TimeUnit.SECONDS))
                assertTrue(released.await(WAIT_SECONDS, TimeUnit.SECONDS))
                assertFalse(server.loaded)
            } finally {
                proceed.countDown()
                executor.shutdownNow()
            }
        }
    }

    private companion object {
        const val WAIT_SECONDS = 60L
    }
}
