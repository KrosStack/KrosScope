package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.cli.parseJson
import kotlinx.serialization.json.JsonObject
import java.io.Writer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class McpTestOutput : Writer() {
    private val pending = StringBuilder()
    private val lines = LinkedBlockingQueue<String>()

    override fun write(bytes: CharArray, offset: Int, length: Int) {
        synchronized(lock) { pending.append(bytes, offset, length) }
    }

    override fun flush() {
        synchronized(lock) {
            lines.put(pending.toString())
            pending.setLength(0)
        }
    }

    fun next(): JsonObject {
        val line = checkNotNull(lines.poll(5, TimeUnit.SECONDS)) { "MCP did not answer" }
        val reply = parseJson(line.removeSuffix("\n"))
        assertEquals(reply.toString() + "\n", line)
        return reply
    }

    fun assertEmpty() {
        assertTrue(lines.isEmpty(), "MCP answered a request while the preceding tool was blocked")
        synchronized(lock) { assertEquals("", pending.toString()) }
    }

    fun remaining(): List<JsonObject> = buildList {
        while (lines.isNotEmpty()) add(next())
        synchronized(lock) { assertEquals("", pending.toString()) }
    }

    override fun close() = Unit
}
