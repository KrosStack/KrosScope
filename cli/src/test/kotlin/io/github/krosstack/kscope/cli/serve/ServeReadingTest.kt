package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.inScannedWorkspace
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServeReadingTest {
    @Test
    fun `silent connections do not delay a complete request and expire`() = inScannedWorkspace { root ->
        val accepted = CountDownLatch(4)
        val settings = ServeSettings(requestTimeoutMillis = 5_000, onConnection = { accepted.countDown() })
        withTestServer(root, settings) { server ->
            val silent = List(4) { server.connect() }
            val executor = Executors.newSingleThreadExecutor()
            try {
                assertTrue(accepted.await(2, TimeUnit.SECONDS))
                val query = executor.submit<ServeAnswer> { server.query(listOf("info")) }
                assertEquals(0, query.get(1, TimeUnit.SECONDS).exitCode)
                silent.forEach {
                    it.soTimeout = 6_000
                    assertEquals(-1, it.getInputStream().read())
                }
            } finally {
                silent.forEach(Socket::close)
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun `only sixteen connections can be reading at once`() = inScannedWorkspace { root ->
        val accepted = CountDownLatch(16)
        val settings = ServeSettings(onConnection = { accepted.countDown() })
        withTestServer(root, settings) { server ->
            val silent = List(16) { server.connect() }
            try {
                assertTrue(accepted.await(2, TimeUnit.SECONDS))
                server.connect().use {
                    it.soTimeout = 1_000
                    assertEquals(-1, it.getInputStream().read())
                }
            } finally {
                silent.forEach(Socket::close)
            }
        }
    }
}
