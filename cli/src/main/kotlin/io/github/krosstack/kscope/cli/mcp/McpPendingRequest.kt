package io.github.krosstack.kscope.cli.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

internal data class McpPendingRequest(val request: McpRequest?, val error: JsonObject?) {
    val id: JsonElement? get() = request?.id ?: error?.get("id")?.takeUnless { it == JsonNull }
    val isToolCall: Boolean get() = request?.method == "tools/call"
}
