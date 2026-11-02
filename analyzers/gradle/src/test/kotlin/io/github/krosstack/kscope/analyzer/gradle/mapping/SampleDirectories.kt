package io.github.krosstack.kscope.analyzer.gradle.mapping

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val DIRECTORY_KEYS = setOf("kotlinDirs", "resourceDirs", "javaDirs", "dirs", "manifest")

/** A recorded sample whose source directories that match are left out, as if they were not on disk. */
internal object SampleDirectories {
    fun without(directories: Regex, name: String = "kmp-android-library.json"): SampleModel =
        SampleModel.load(name) { pruned(Json.parseToJsonElement(it), directories, inDirectories = false).toString() }

    private fun pruned(element: JsonElement, directories: Regex, inDirectories: Boolean): JsonElement = when (element) {
        is JsonArray -> JsonArray(
            element.filterNot { inDirectories && it.isPathIn(directories) }
                .map { pruned(it, directories, inDirectories) },
        )

        is JsonObject -> JsonObject(
            element.mapValues { (key, value) ->
                val nested = inDirectories || key in DIRECTORY_KEYS
                if (nested && value.isPathIn(directories)) JsonNull else pruned(value, directories, nested)
            },
        )

        else -> element
    }

    private fun JsonElement.isPathIn(directories: Regex) =
        this is JsonPrimitive && isString && directories.matches(content)
}
