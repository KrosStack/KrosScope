package io.github.krosstack.kscope.benchmark.agent

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

internal val agentJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = true
}

private val eventJson = Json { isLenient = false }

internal fun parseJsonObject(text: String): JsonObject? = try {
    eventJson.parseToJsonElement(text) as? JsonObject
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

internal fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.content

internal fun JsonObject.long(key: String): Long? = primitive(key)?.takeIf { !it.isString }?.longOrNull

internal fun JsonObject.double(key: String): Double? = primitive(key)?.takeIf { !it.isString }?.doubleOrNull

internal fun JsonObject.bool(key: String): Boolean? = primitive(key)?.takeIf { !it.isString }?.booleanOrNull

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.items(key: String): List<JsonObject> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

internal fun JsonObject.strings(key: String): List<String> = (this[key] as? JsonArray).orEmpty()
    .mapNotNull { (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content }

internal fun JsonObject.count(key: String): Int = (this[key] as? JsonArray)?.size ?: 0

internal fun JsonElement?.textContent(): String = when (this) {
    is JsonPrimitive -> if (isString) content else ""
    is JsonArray -> mapNotNull { (it as? JsonObject)?.string("text") }.joinToString("\n")
    else -> ""
}
