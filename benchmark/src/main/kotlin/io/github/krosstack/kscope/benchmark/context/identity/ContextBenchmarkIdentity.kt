package io.github.krosstack.kscope.benchmark.context.identity

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkOptions
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkTask
import io.github.krosstack.kscope.benchmark.context.contextFingerprint
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.retrieval.MlMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

internal object ContextBenchmarkIdentity {
    fun fingerprint(value: JsonElement): ContentHash = ContentHash(contextFingerprint(canonical(value).toString()))

    fun task(snapshot: String, task: ContextBenchmarkTask, options: ContextBenchmarkOptions): String = fingerprint(
        buildJsonObject {
            put("snapshot", snapshot)
            put("task", KscopeJson.encodeToJsonElement(task))
            put("budgets", KscopeJson.encodeToJsonElement(options.budgets))
            put("methods", KscopeJson.encodeToJsonElement(options.methods))
            put("analysisKeys", KscopeJson.encodeToJsonElement(options.selection.analysisKeys))
            put("pipeline", KscopeJson.encodeToJsonElement(options.selection.pipeline))
            put("estimator", KscopeJson.encodeToJsonElement(options.estimator.id))
        },
    ).value

    fun legacyTask(snapshot: String, task: ContextBenchmarkTask, options: ContextBenchmarkOptions): String? =
        if (options.selection.pipeline == null && options.methods.all { it.ml == MlMode.OFF }) {
            val configuration = "${options.budgets}:${options.methods}:${options.estimator.id}"
            contextFingerprint(snapshot + configuration + Json.encodeToString(task))
        } else {
            null
        }

    fun bind(caller: String, keys: List<ContextAnalysisKey>): String =
        if (keys.isEmpty() || keys == ContextBenchmarkSelection(ContextBenchmarkOptions.DEFAULT_METHODS).analysisKeys) {
            caller
        } else {
            fingerprint(
                buildJsonObject {
                    put("caller", caller)
                    put("analysisKeys", KscopeJson.encodeToJsonElement(keys))
                },
            ).value
        }

    private fun canonical(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonical(it.value) })
        is JsonArray -> JsonArray(value.map(::canonical))
        else -> value
    }
}
