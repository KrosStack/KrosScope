package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.service.KrosScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.nio.file.Path

internal val modernMeta = buildJsonObject {
    put("io.modelcontextprotocol/protocolVersion", "2026-07-28")
    put("io.modelcontextprotocol/clientCapabilities", buildJsonObject {})
}

internal fun rpc(method: String, params: JsonObject = buildJsonObject {}, id: JsonElement = JsonPrimitive(1)): String =
    buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("method", method)
        put("params", params)
    }.toString()

internal fun modernRpc(method: String, params: JsonObject = buildJsonObject {}): String =
    rpc(method, JsonObject(params + ("_meta" to modernMeta)))

internal fun callParams(name: String, arguments: JsonElement = buildJsonObject {}): JsonObject = buildJsonObject {
    put("name", name)
    put("arguments", arguments)
}

internal fun initialize(version: String = "2025-11-25"): String = rpc(
    "initialize",
    buildJsonObject { put("protocolVersion", version) },
)

internal fun mcpTranscript(
    root: Path,
    lines: List<String>,
    settings: McpSettings = McpSettings(noServer = true),
): Pair<List<JsonObject>, String> {
    val output = StringWriter()
    val error = StringWriter()
    val input = ByteArrayInputStream((lines.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8))
    McpSession(root, McpStreams(input, output, error), settings).run()
    val replies = output.toString().lines().dropLast(1).map {
        parseJson(it).also { parsed -> kotlin.test.assertEquals(it, parsed.toString()) }
    }
    return replies to error.toString()
}

internal fun toolText(result: JsonObject): String =
    ((result.getValue("content") as JsonArray).single().jsonObject.getValue("text") as JsonPrimitive).content

internal fun assertToolResult(result: JsonObject, modern: Boolean, error: Boolean, text: String) {
    val expected = buildJsonObject {
        if (modern) put("resultType", "complete")
        put(
            "content",
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    },
                ),
            ),
        )
        put("isError", error)
    }
    kotlin.test.assertEquals(expected, result)
}

internal val stableMcpScan = ScanRunner { workspace, options, rebuild, verify ->
    KrosScope.open(workspace, options).use { it.scan(rebuild, verify) }.copy(stageMillis = emptyMap())
}
