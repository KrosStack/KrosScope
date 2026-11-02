package io.github.krosstack.kscope.benchmark.schema

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Whether a JSON value is of a type that a schema names. */
internal object JsonTypes {
    private val booleans = setOf("true", "false")

    fun matches(type: String, value: JsonElement): Boolean = when (type) {
        "object" -> value is JsonObject
        "array" -> value is JsonArray
        "null" -> value is JsonNull
        else -> value is JsonPrimitive && value !is JsonNull && scalarMatches(type, value)
    }

    private fun scalarMatches(type: String, value: JsonPrimitive): Boolean = when (type) {
        "string" -> value.isString
        "boolean" -> !value.isString && value.content in booleans
        "integer" -> !value.isString && value.content.toLongOrNull() != null
        else -> false
    }
}
