package io.github.krosstack.kscope.cli.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

internal data class McpArgument(
    val name: String,
    val description: String,
    val required: Boolean = true,
    val choices: List<String> = emptyList(),
    val integerRange: IntRange? = null,
) {
    fun schema(): JsonObject = buildJsonObject {
        put("type", if (integerRange == null) "string" else "integer")
        put("description", description)
        if (integerRange == null) put("maxLength", MAX_STRING_LENGTH)
        integerRange?.let {
            put("minimum", it.first)
            put("maximum", it.last)
        }
        if (choices.isNotEmpty()) put("enum", JsonArray(choices.map(::JsonPrimitive)))
    }

    fun error(value: JsonElement?): String? {
        if (value == null) return if (required) "Missing required argument: $name" else null
        return if (value is JsonPrimitive) primitiveError(value) else "Argument $name has the wrong type"
    }

    private fun primitiveError(primitive: JsonPrimitive): String? {
        val range = integerRange
        if (range != null) {
            val number = if (primitive.isString) null else primitive.intOrNull
            return if (number != null && number in range) null else "Argument $name must be an integer in $range"
        }
        return stringError(primitive)
    }

    private fun stringError(value: JsonPrimitive): String? = when {
        !value.isString -> "Argument $name must be a string"

        value.content.codePointCount(0, value.content.length) > MAX_STRING_LENGTH ->
            "Argument $name exceeds $MAX_STRING_LENGTH characters"

        choices.isNotEmpty() && value.content !in choices -> "Argument $name must be one of ${choices.joinToString()}"

        else -> null
    }

    companion object {
        const val MAX_STRING_LENGTH = 20_000
    }
}
