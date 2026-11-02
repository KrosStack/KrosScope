package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.cli.inWorkspace
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpConcurrencyTest {
    @Test
    fun `ping list opening and protocol errors wait for the preceding tool and answer in order`() =
        inWorkspace { root ->
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val hook = {
                started.countDown()
                release.await()
            }
            McpAsyncSession(root, hook, release::countDown).use { session ->
                session.send(session.tool(JsonPrimitive(2)))
                assertTrue(started.await(5, TimeUnit.SECONDS))
                session.send(
                    rpc("ping", id = JsonPrimitive(3)),
                    rpc("tools/list", id = JsonPrimitive(4)),
                    rpc("server/discover", id = JsonPrimitive(5)),
                    rpc("initialize", id = JsonPrimitive(6)),
                    rpc("unknown", id = JsonPrimitive(7)),
                    rpc("tools/call", callParams("unknown"), JsonPrimitive(8)),
                    "not JSON",
                    "x".repeat(1048577),
                    rpc("ping", id = JsonPrimitive(9)),
                )
                session.awaitRead()
                session.assertEmpty()
                release.countDown()
                session.next(2)
                for (id in 3..6) assertTrue("result" in session.next(id))
                assertEquals(JsonPrimitive(-32601), session.next(7).getValue("error").jsonObject["code"])
                assertEquals(JsonPrimitive(-32602), session.next(8).getValue("error").jsonObject["code"])
                assertEquals(JsonPrimitive(-32700), session.next(JsonNull).getValue("error").jsonObject["code"])
                assertEquals(JsonPrimitive(-32600), session.next(JsonNull).getValue("error").jsonObject["code"])
                session.next(9)
                assertEquals(emptyList(), session.finish())
            }
        }

    @Test
    fun `requests beyond the sixty four that wait are answered at once with an overload error`() = inWorkspace { root ->
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
            val requests = (3..100).map { session.tool(JsonPrimitive(it)) }.joinToString("\n")
            session.send(requests)
            for (id in 67..100) {
                assertEquals(JsonPrimitive(-32000), session.next(id).getValue("error").jsonObject["code"])
            }
            session.assertEmpty()
            release.countDown()
            assertEquals((2..66).map(::JsonPrimitive), session.finish().map { it["id"] })
            assertEquals(65, calls.get())
        }
    }

    @Test
    fun `two calls execute serially and responses are whole lines`() = inWorkspace { root ->
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val calls = AtomicInteger()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val hook = {
            maximum.accumulateAndGet(active.incrementAndGet(), ::maxOf)
            if (calls.incrementAndGet() == 1) {
                started.countDown()
                release.await()
            }
            active.decrementAndGet()
            Unit
        }
        McpAsyncSession(root, hook, release::countDown).use { session ->
            session.send(session.tool(JsonPrimitive(2)), session.tool(JsonPrimitive(3)))
            assertTrue(started.await(5, TimeUnit.SECONDS))
            session.send(rpc("ping", id = JsonPrimitive(4)))
            session.awaitRead()
            session.assertEmpty()
            assertEquals(1, calls.get())
            release.countDown()
            session.next(2)
            session.next(3)
            session.next(4)
            assertEquals(emptyList(), session.finish())
            assertEquals(2, calls.get())
            assertEquals(1, maximum.get())
        }
    }

    @Test
    fun `eof drains one running call and two waiting calls in order before ending`() = inWorkspace { root ->
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
            session.send(session.tool(JsonPrimitive(3)), session.tool(JsonPrimitive(4)))
            session.endInput()
            session.assertEmpty()
            release.countDown()
            assertEquals((2..4).map(::JsonPrimitive), session.finish().map { it["id"] })
            assertEquals(3, calls.get())
        }
    }

    @Test
    fun `a tool retains its request era before later opening requests execute`() = inWorkspace { root ->
        for (modern in listOf(false, true)) {
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val hook = {
                started.countDown()
                release.await()
            }
            McpAsyncSession(root, hook, release::countDown, modern).use { session ->
                session.send(session.tool(JsonPrimitive(2), modern))
                assertTrue(started.await(5, TimeUnit.SECONDS))
                val opening = if (modern) "initialize" else "server/discover"
                session.send(rpc(opening, id = JsonPrimitive(3)))
                session.awaitRead()
                session.assertEmpty()
                release.countDown()
                val result = session.next(2).getValue("result").jsonObject
                assertEquals(modern, "resultType" in result)
                session.next(3)
                assertEquals(emptyList(), session.finish())
            }
        }
    }
}
