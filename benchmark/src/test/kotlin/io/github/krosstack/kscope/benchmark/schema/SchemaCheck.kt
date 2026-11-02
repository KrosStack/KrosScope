package io.github.krosstack.kscope.benchmark.schema

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Checks a JSON value against the part of Draft 2020-12 that `info` uses: `$ref`, `type`, `const`, `enum`,
 * `properties`, `required`, `additionalProperties`, `items`, `anyOf` and `oneOf`. It answers with what the value does
 * wrong, one text for each violation, and an empty list for a value that conforms.
 */
internal class SchemaCheck(private val document: JsonObject) {
    fun violations(value: JsonElement): List<String> = validate(document, value, "$")

    private fun validate(schema: JsonObject, value: JsonElement, path: String): List<String> {
        val reference = schema["\$ref"]?.jsonPrimitive?.content
        return if (reference != null) {
            validate(definition(reference), value, path)
        } else {
            typeViolations(schema, value, path) + literalViolations(schema, value, path) +
                alternatives(schema, value, path) + structure(schema, value, path)
        }
    }

    private fun definition(reference: String): JsonObject =
        document.getValue("\$defs").jsonObject.getValue(reference.removePrefix("#/\$defs/")).jsonObject

    private fun typeViolations(schema: JsonObject, value: JsonElement, path: String): List<String> {
        val declared = schema["type"] ?: return emptyList()
        val types = (declared as? JsonArray ?: JsonArray(listOf(declared))).map { it.jsonPrimitive.content }
        val problem = "$path: expected $types but found $value"
        return if (types.any { JsonTypes.matches(it, value) }) emptyList() else listOf(problem)
    }

    private fun literalViolations(schema: JsonObject, value: JsonElement, path: String): List<String> = buildList {
        val const = schema["const"]
        if (const != null && const != value) add("$path: expected $const but found $value")
        val enum = schema["enum"]?.jsonArray
        if (enum != null && value !in enum) add("$path: $value is not one of $enum")
    }

    private fun alternatives(schema: JsonObject, value: JsonElement, path: String): List<String> = buildList {
        val any = schema["anyOf"]?.jsonArray
        if (any != null && any.none { validate(it.jsonObject, value, path).isEmpty() }) add("$path: matches no anyOf")
        val one = schema["oneOf"]?.jsonArray
        val matching = one?.count { validate(it.jsonObject, value, path).isEmpty() }
        if (matching != null && matching != 1) add("$path: matches $matching of oneOf")
    }

    private fun structure(schema: JsonObject, value: JsonElement, path: String): List<String> = when (value) {
        is JsonObject -> objectViolations(schema, value, path)
        is JsonArray -> itemViolations(schema, value, path)
        else -> emptyList()
    }

    private fun itemViolations(schema: JsonObject, value: JsonArray, path: String): List<String> {
        val items = schema["items"]?.jsonObject ?: return emptyList()
        return value.flatMapIndexed { index, element -> validate(items, element, "$path[$index]") }
    }

    private fun objectViolations(schema: JsonObject, value: JsonObject, path: String): List<String> {
        val properties = schema["properties"]?.jsonObject ?: JsonObject(emptyMap())
        val missing = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty().filter { it !in value }
        val additional = schema["additionalProperties"]
        val others = value.filterKeys { it !in properties }
        val forbidden = additional == JsonPrimitive(false)
        val unexpected = if (forbidden) others.keys.map { "$path: unexpected $it" } else emptyList()
        val described = (additional as? JsonObject)?.let { rule ->
            others.flatMap { (key, element) -> validate(rule, element, "$path.$key") }
        }.orEmpty()
        val known = value.flatMap { (key, element) ->
            properties[key]?.let { validate(it.jsonObject, element, "$path.$key") }.orEmpty()
        }
        return missing.map { "$path: missing $it" } + unexpected + described + known
    }
}
