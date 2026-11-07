package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.retrieval.AnalysisResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/** Renders results as JSON. Keys follow the declaration order of the result types. */
public object ServiceJson {
    @PublishedApi
    internal val json: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        explicitNulls = true
    }

    public inline fun <reified T> encode(result: T): String =
        render(json.encodeToJsonElement(result).jsonObject, null, if (result is AnalysisResult) 2 else 1)

    /** Renders a result of a query with `indexLevel`, the level of the index that answered, after `schemaVersion`. */
    public inline fun <reified T> encode(result: T, level: ScanLevel?): String = render(
        json.encodeToJsonElement(result).jsonObject,
        JsonPrimitive(level?.wireName),
        if (result is AnalysisResult) 2 else 1,
    )

    private const val INDEX_LEVEL = "indexLevel"

    @PublishedApi
    internal fun render(fields: JsonObject, level: JsonElement?, version: Int): String {
        val own = level ?: fields[INDEX_LEVEL]
        val leading = listOfNotNull("schemaVersion" to JsonPrimitive(version), own?.let { INDEX_LEVEL to it })
        val rest = fields - "schemaVersion" - INDEX_LEVEL
        return json.encodeToString(JsonObject.serializer(), JsonObject(leading.toMap() + rest))
    }
}
