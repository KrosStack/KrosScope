package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkResult
import io.github.krosstack.kscope.benchmark.context.ContextMeasurementDefinition
import io.github.krosstack.kscope.benchmark.localization.LocalizationBenchmark
import io.github.krosstack.kscope.benchmark.localization.LocalizationCorpusSummary
import io.github.krosstack.kscope.benchmark.localization.LocalizationGatePolicy
import io.github.krosstack.kscope.model.context.TokenizerId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class BenchmarkEnvelopeTest {
    @Test
    fun `benchmark envelopes include schema version while unversioned historical localization remains readable`() {
        val suite = LocalizationBenchmark.combine(emptyList())
        val current = Json.parseToJsonElement(BenchmarkJson.encode(suite)).jsonObject
        val historical = JsonObject(current - setOf("schemaVersion", "policy"))
        val decoded = BenchmarkJson.decodeLocalization(historical.toString())

        assertEquals("1", current.getValue("schemaVersion").jsonPrimitive.content)
        assertEquals(1, decoded.schemaVersion)
        assertEquals(LocalizationGatePolicy.STRONGEST_LEXICAL, decoded.policy)
    }

    @Test
    fun `context benchmark schema version belongs to the top level envelope`() {
        val context = ContextBenchmarkResult(
            emptyList(),
            TokenizerId("test"),
            LocalizationCorpusSummary(),
            ContextMeasurementDefinition(),
            false,
            false,
            false,
        )
        val encoded = Json.parseToJsonElement(BenchmarkJson.encode(context)).jsonObject

        assertEquals("1", encoded.getValue("schemaVersion").jsonPrimitive.content)
    }
}
