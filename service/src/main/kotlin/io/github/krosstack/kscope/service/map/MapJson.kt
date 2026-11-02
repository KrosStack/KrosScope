package io.github.krosstack.kscope.service.map

import kotlinx.serialization.json.Json

/** Renders the results of the map compactly: a field that holds its default is left out. */
public object MapJson {
    @PublishedApi
    internal val json: Json = Json {
        encodeDefaults = false
        explicitNulls = false
    }

    public inline fun <reified T> encode(value: T): String = json.encodeToString(value)
}
