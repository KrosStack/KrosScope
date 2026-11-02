package io.github.krosstack.kscope.cli.mcp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object McpMetadata {
    private const val INVALID_PARAMS = -32602
    fun error(request: McpRequest): JsonObject? {
        val meta = request.params["_meta"] as? JsonObject
        val version = meta?.get("io.modelcontextprotocol/protocolVersion") as? JsonPrimitive
        val capabilities = meta?.get("io.modelcontextprotocol/clientCapabilities") as? JsonObject
        val valid = version?.isString == true && capabilities != null
        return when {
            !valid -> McpResults.error(request.id, INVALID_PARAMS, "Missing or invalid protocol metadata")

            version.content != McpResults.VERSION ->
                McpResults.versionError(checkNotNull(request.id), version)

            else -> null
        }
    }
}
