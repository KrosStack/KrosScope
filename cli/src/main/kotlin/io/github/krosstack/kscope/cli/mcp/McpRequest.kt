package io.github.krosstack.kscope.cli.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal data class McpRequest(val id: JsonElement?, val method: String, val params: JsonObject) {
    companion object {
        fun from(element: JsonElement): McpRequest? {
            val objectValue = element as? JsonObject ?: return null
            return request(objectValue)
        }

        fun id(element: JsonElement): JsonElement? = (element as? JsonObject)?.get("id")?.takeIf(::validId)

        fun isNotification(element: JsonElement): Boolean {
            val value = element as? JsonObject ?: return false
            val method = value["method"] as? JsonPrimitive
            return value["jsonrpc"] == JsonPrimitive("2.0") && method?.isString == true && "id" !in value
        }

        private fun request(objectValue: JsonObject): McpRequest? {
            val method = objectValue["method"] as? JsonPrimitive
            val valid = objectValue["jsonrpc"] == JsonPrimitive("2.0") && method?.isString == true
            val hasValidId = "id" !in objectValue || validId(objectValue["id"])
            val params = objectValue["params"] ?: JsonObject(emptyMap())
            return if (valid && hasValidId && params is JsonObject) {
                McpRequest(objectValue["id"], checkNotNull(method).content, params)
            } else {
                null
            }
        }

        private fun validId(id: JsonElement?): Boolean {
            val primitive = id as? JsonPrimitive ?: return false
            return primitive.isString || primitive.content.toDoubleOrNull() != null
        }
    }
}
