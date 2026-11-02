package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object McpJson {
    private const val SPACE = 32
    private val number = Regex("""-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?""")

    fun parse(line: String): JsonElement {
        if (!validStrings(line)) throw SerializationException("Invalid JSON string")
        val value = KscopeJson.parseToJsonElement(line)
        if (!validValue(value)) throw SerializationException("Invalid JSON literal")
        return value
    }

    private fun validValue(value: JsonElement): Boolean = when (value) {
        is JsonArray -> value.all(::validValue)
        is JsonObject -> value.values.all(::validValue)
        is JsonPrimitive -> validPrimitive(value)
    }

    private fun validPrimitive(value: JsonPrimitive): Boolean = when {
        value.isString -> true
        value == JsonNull -> true
        value.content == "true" || value.content == "false" -> true
        else -> number.matches(value.content)
    }

    private fun validStrings(line: String): Boolean {
        var quoted = false
        var escaped = false
        for (character in line) {
            if (quoted && character.code < SPACE) return false
            when {
                quoted && escaped -> escaped = false
                character == '"' -> quoted = !quoted
                else -> escaped = quoted && character == '\\'
            }
        }
        return !quoted
    }
}
