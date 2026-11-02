package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.service.KrosScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object McpResults {
    const val VERSION = "2026-07-28"
    private const val TTL_MILLIS = 3_600_000
    private const val UNSUPPORTED_VERSION = -32022
    private const val OVERLOADED = -32000
    private val supported = JsonArray(listOf(JsonPrimitive(VERSION)))
    private val info = buildJsonObject {
        put("name", "kscope")
        put("version", KrosScope.toolVersion())
    }
    private val capabilities = buildJsonObject { put("tools", buildJsonObject {}) }

    fun initialize(params: JsonObject): JsonObject = buildJsonObject {
        val requested = (params["protocolVersion"] as? JsonPrimitive)?.content
        val versions = setOf("2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25")
        put("protocolVersion", requested?.takeIf { it in versions } ?: "2025-11-25")
        put("capabilities", capabilities)
        put("serverInfo", info)
        put("instructions", McpProtocol.INSTRUCTIONS)
    }

    fun discover(): JsonObject = buildJsonObject {
        put("resultType", "complete")
        put("supportedVersions", supported)
        put("capabilities", capabilities)
        put("_meta", buildJsonObject { put("io.modelcontextprotocol/serverInfo", info) })
        put("instructions", McpProtocol.INSTRUCTIONS)
        put("ttlMs", TTL_MILLIS)
        put("cacheScope", "private")
    }

    fun complete(modern: Boolean, fields: JsonObject = JsonObject(emptyMap())): JsonObject = buildJsonObject {
        if (modern) put("resultType", "complete")
        fields.forEach { (key, value) -> put(key, value) }
    }

    fun tools(modern: Boolean): JsonObject = complete(
        modern,
        buildJsonObject {
            put("tools", McpTools.catalogue)
            if (modern) {
                put("ttlMs", TTL_MILLIS)
                put("cacheScope", "private")
            }
        },
    )

    fun tool(result: McpToolResult, modern: Boolean): JsonObject = complete(
        modern,
        buildJsonObject {
            put(
                "content",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("type", "text")
                            put("text", result.text)
                        },
                    ),
                ),
            )
            put("isError", result.isError)
        },
    )

    fun result(id: JsonElement, result: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

    fun error(id: JsonElement?, code: Int, message: String, data: JsonObject? = null): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        put(
            "error",
            buildJsonObject {
                put("code", code)
                put("message", message)
                if (data != null) put("data", data)
            },
        )
    }

    fun overloaded(id: JsonElement?): JsonObject = error(id, OVERLOADED, "Too many pending requests")

    fun versionError(id: JsonElement, requested: JsonElement): JsonObject = error(
        id,
        UNSUPPORTED_VERSION,
        "Unsupported protocol version",
        buildJsonObject {
            put("supported", supported)
            put("requested", requested)
        },
    )
}
