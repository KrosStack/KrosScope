package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.cli.inWorkspace
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpCancellationTest {
    @Test
    fun `a running cancellation interrupts immediately discards its result and clears interruption`() =
        inWorkspace { root ->
            val started = CountDownLatch(1)
            val interrupted = CountDownLatch(1)
            val release = CountDownLatch(1)
            val calls = AtomicInteger()
            val leaked = AtomicBoolean()
            val hook = {
                if (calls.incrementAndGet() == 1) {
                    started.countDown()
                    try {
                        release.await()
                    } catch (_: InterruptedException) {
                        interrupted.countDown()
                        release.await()
                        Thread.currentThread().interrupt()
                    }
                } else {
                    leaked.set(Thread.currentThread().isInterrupted)
                }
            }
            McpAsyncSession(root, hook, release::countDown).use { session ->
                session.send(session.tool(JsonPrimitive(2)))
                assertTrue(started.await(5, TimeUnit.SECONDS))
                session.send(
                    session.cancel(JsonPrimitive(2)),
                    session.tool(JsonPrimitive(3)),
                    rpc("ping", id = JsonPrimitive(4)),
                )
                assertTrue(interrupted.await(5, TimeUnit.SECONDS))
                session.assertEmpty()
                release.countDown()
                assertToolResult(session.next(3).getValue("result").jsonObject, false, false, "tool completed")
                session.next(4)
                assertEquals(emptyList(), session.finish())
                assertFalse(leaked.get())
            }
        }

    @Test
    fun `a waiting cancellation never runs and neighbours answer in order`() = inWorkspace { root ->
        val calls = AtomicInteger()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val hook = {
            if (calls.incrementAndGet() == 1) {
                started.countDown()
                release.await()
            }
        }
        McpAsyncSession(root, hook, release::countDown).use { session ->
            session.send(session.tool(JsonPrimitive(2)))
            assertTrue(started.await(5, TimeUnit.SECONDS))
            session.send(
                session.tool(JsonPrimitive(3)),
                session.tool(JsonPrimitive(4)),
                session.cancel(JsonPrimitive(3)),
                rpc("ping", id = JsonPrimitive(5)),
            )
            session.awaitRead()
            session.assertEmpty()
            release.countDown()
            session.next(2)
            session.next(4)
            session.next(5)
            assertEquals(emptyList(), session.finish())
            assertEquals(2, calls.get())
        }
    }

    @Test
    fun `unknown missing differently typed and already answered cancellation ids change nothing`() =
        inWorkspace { root ->
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val interrupted = AtomicInteger()
            val hook = {
                started.countDown()
                try {
                    release.await()
                } catch (_: InterruptedException) {
                    interrupted.incrementAndGet()
                }
                Unit
            }
            McpAsyncSession(root, hook, release::countDown).use { session ->
                val id = JsonPrimitive("1")
                session.send(session.tool(id))
                assertTrue(started.await(5, TimeUnit.SECONDS))
                session.send(
                    session.cancel(JsonPrimitive("unknown")),
                    session.cancel(),
                    session.cancel(JsonPrimitive(1)),
                    rpc("ping", id = JsonPrimitive(3)),
                )
                session.awaitRead()
                session.assertEmpty()
                release.countDown()
                session.next(id)
                session.next(3)
                session.send(session.cancel(id), rpc("ping", id = JsonPrimitive(4)))
                session.next(4)
                assertEquals(emptyList(), session.finish())
                assertEquals(0, interrupted.get())
            }
        }

    @Test
    fun `a cancelled waiting initialize is skipped without changing protocol state`() = inWorkspace { root ->
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val hook = {
            started.countDown()
            release.await()
        }
        McpAsyncSession(root, hook, release::countDown, modern = true).use { session ->
            session.send(session.tool(JsonPrimitive(2), modern = true))
            assertTrue(started.await(5, TimeUnit.SECONDS))
            session.send(
                rpc("initialize", id = JsonPrimitive(3)),
                session.cancel(JsonPrimitive(3)),
                rpc("ping", buildJsonObject { put("_meta", modernMeta) }, JsonPrimitive(4)),
            )
            session.awaitRead()
            release.countDown()
            session.next(2)
            assertEquals(JsonPrimitive("complete"), session.next(4).getValue("result").jsonObject["resultType"])
            assertEquals(emptyList(), session.finish())
        }
    }

    @Test
    fun `a cancelled tool failure sends no error and the next request succeeds`() = inWorkspace { root ->
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val hook = {
            started.countDown()
            try {
                release.await()
            } catch (failure: InterruptedException) {
                interrupted.countDown()
                throw failure
            }
        }
        McpAsyncSession(root, hook, release::countDown).use { session ->
            session.send(session.tool(JsonPrimitive(2)))
            assertTrue(started.await(5, TimeUnit.SECONDS))
            session.send(session.cancel(JsonPrimitive(2)), rpc("ping", id = JsonPrimitive(3)))
            assertTrue(interrupted.await(5, TimeUnit.SECONDS))
            assertTrue("result" in session.next(3))
            assertEquals(emptyList(), session.finish())
        }
    }

    @Test
    fun `a cancellation is read while the queue is full and interrupts the running call`() = inWorkspace { root ->
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val hook = {
            if (calls.incrementAndGet() == 1) {
                started.countDown()
                try {
                    release.await()
                } catch (_: InterruptedException) {
                    interrupted.countDown()
                }
            }
        }
        McpAsyncSession(root, hook, release::countDown).use { session ->
            session.send(session.tool(JsonPrimitive(2)))
            assertTrue(started.await(5, TimeUnit.SECONDS))
            session.send((3..66).joinToString("\n") { session.tool(JsonPrimitive(it)) })
            session.send(session.cancel(JsonPrimitive(2)))
            assertTrue(interrupted.await(5, TimeUnit.SECONDS))
            for (id in 3..66) session.next(id)
            assertEquals(emptyList(), session.finish())
            assertEquals(65, calls.get())
        }
    }
}
