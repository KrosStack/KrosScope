package io.github.krosstack.kscope.cli.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.StringWriter
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class McpAsyncSession(root: Path, hook: () -> Unit, private val release: () -> Unit, modern: Boolean = false) :
    AutoCloseable {
    private val input = McpTestInput()
    private val output = McpTestOutput()
    private val executor = Executors.newSingleThreadExecutor()
    private val task = executor.submit {
        val settings = McpSettings(
            noServer = true,
            executeTool = {
                hook()
                McpToolResult("tool completed")
            },
        )
        McpSession(root, McpStreams(input, output, StringWriter()), settings).run()
    }

    init {
        send(if (modern) rpc("server/discover") else initialize())
        next(1)
    }

    fun send(vararg lines: String) = input.send(lines.toList())

    fun awaitRead() = input.awaitRead()

    fun assertEmpty() = output.assertEmpty()

    fun endInput() {
        input.close()
        input.awaitEnd()
    }

    fun tool(id: JsonElement, modern: Boolean = false): String {
        val params = callParams("scan")
        val metadata = if (modern) JsonObject(params + ("_meta" to modernMeta)) else params
        return rpc("tools/call", metadata, id)
    }

    fun cancel(id: JsonElement? = null): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("method", "notifications/cancelled")
        put("params", buildJsonObject { id?.let { put("requestId", it) } })
    }.toString()

    fun next(id: Int): JsonObject = next(JsonPrimitive(id))

    fun next(id: JsonElement): JsonObject = output.next().also { assertEquals(id, it["id"]) }

    fun finish(): List<JsonObject> {
        input.close()
        task.get(5, TimeUnit.SECONDS)
        return output.remaining()
    }

    override fun close() {
        input.close()
        release()
        try {
            task.get(5, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
