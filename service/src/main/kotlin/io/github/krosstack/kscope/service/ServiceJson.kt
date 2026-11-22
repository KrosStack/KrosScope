package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.service.analysis.AnalysisJson
import io.github.krosstack.kscope.service.freshness.IndexFreshness
import io.github.krosstack.kscope.service.lookup.SearchOrigin
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
        render(json.encodeToJsonElement(result).jsonObject, null, schemaVersionOf(result))

    /** Renders a result of a query with `indexLevel`, the level of the index that answered, after `schemaVersion`. */
    public inline fun <reified T> encode(
        result: T,
        level: ScanLevel?,
        indexLocation: String? = null,
        freshness: IndexFreshness? = null,
        search: SearchOrigin? = null,
    ): String = render(
        json.encodeToJsonElement(result).jsonObject,
        JsonPrimitive(level?.wireName),
        schemaVersionOf(result),
        listOfNotNull(
            indexLocation?.let { INDEX_LOCATION to JsonPrimitive(it) },
        ) + search?.fields().orEmpty() + listOfNotNull(
            freshness?.let { INDEX_FRESHNESS to json.encodeToJsonElement(it) },
        ),
    )

    private const val INDEX_LEVEL = "indexLevel"
    private const val ANALYSIS_VERSION = 3

    @PublishedApi
    internal fun schemaVersionOf(result: Any?): Int =
        if (result is AnalysisResult || result is AnalysisJson) ANALYSIS_VERSION else 1

    @PublishedApi
    internal const val INDEX_LOCATION: String = "indexLocation"

    @PublishedApi
    internal const val INDEX_FRESHNESS: String = "indexFreshness"

    @PublishedApi
    internal fun render(
        fields: JsonObject,
        level: JsonElement?,
        version: Int,
        origin: List<Pair<String, JsonElement>> = emptyList(),
    ): String {
        val own = level ?: fields[INDEX_LEVEL]
        val leading = listOfNotNull(
            "schemaVersion" to JsonPrimitive(version),
            own?.let { INDEX_LEVEL to it },
        ) + origin
        val rest = fields - "schemaVersion" - INDEX_LEVEL
        return json.encodeToString(JsonObject.serializer(), JsonObject(leading.toMap() + rest))
    }
}
