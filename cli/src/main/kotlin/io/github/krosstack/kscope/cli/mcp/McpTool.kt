package io.github.krosstack.kscope.cli.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class McpTool(
    val name: String,
    val title: String,
    val description: String,
    val fields: List<McpArgument> = emptyList(),
) {
    fun schema(): JsonObject = buildJsonObject {
        put("name", name)
        put("title", title)
        put("description", description)
        put(
            "inputSchema",
            buildJsonObject {
                put("type", "object")
                put("additionalProperties", false)
                put("properties", buildJsonObject { fields.forEach { put(it.name, it.schema()) } })
                put("required", JsonArray(fields.filter { it.required }.map { JsonPrimitive(it.name) }))
            },
        )
    }

    fun call(arguments: JsonElement?, execute: (List<String>) -> McpToolResult): McpToolResult {
        val values = arguments ?: JsonObject(emptyMap())
        if (values !is JsonObject) return McpToolResult("Tool arguments must be an object", true)
        val error = validationError(values)
        return if (error == null) execute(command(values)) else McpToolResult(error, true)
    }

    private fun validationError(values: JsonObject): String? {
        val unknown = values.keys - fields.map { it.name }.toSet()
        return if (unknown.isNotEmpty()) {
            "Unknown argument: ${unknown.sorted().joinToString()}"
        } else {
            fields.firstNotNullOfOrNull { it.error(values[it.name]) }
        }
    }

    private fun command(values: JsonObject): List<String> {
        val options = fields.filterNot { it.required }.flatMap { field ->
            values[field.name]?.let {
                val value = when {
                    field.booleanDefault == null -> (it as JsonPrimitive).content
                    it == JsonPrimitive(false) -> "off"
                    else -> "on"
                }
                listOf("--${field.name}", value)
            }.orEmpty()
        }
        val text = fields.firstOrNull { it.required }?.let { (values.getValue(it.name) as JsonPrimitive).content }
        val format = when (name) {
            "scan" -> listOf("--quiet", "--level", "resolved")
            "info" -> emptyList()
            else -> listOf("--format", "markdown")
        }
        return listOf(name) + format + options + if (text == null) emptyList() else listOf("--", text)
    }
}
