package io.github.krosstack.kscope.benchmark.schema

import io.github.krosstack.kscope.benchmark.BenchmarkJson
import io.github.krosstack.kscope.benchmark.context.ContextAnalysisIdentityTest.Fixture
import io.github.krosstack.kscope.benchmark.context.ContextBenchmark
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkOptions
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkResult
import io.github.krosstack.kscope.benchmark.context.CountingEstimator
import io.github.krosstack.kscope.benchmark.context.identity.ContextBenchmarkSelection
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.retrieval.SemanticState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContextSemanticSchemaTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `context benchmark v2 records arm status and pipeline identity`() {
        Fixture(directory).use { fixture ->
            val options = ContextBenchmarkOptions(
                selection = ContextBenchmarkSelection(ContextBenchmarkOptions.SEMANTIC_METHODS, fixture.pipeline),
                estimator = CountingEstimator(),
            )
            val result = ContextBenchmark.run(fixture.scope, fixture.root, listOf(fixture.task), options = options)
            val encoded = BenchmarkJson.encode(result)
            assertEquals(2, result.schemaVersion)
            assertEquals(result, KscopeJson.decodeFromString<ContextBenchmarkResult>(encoded))
            assertEquals(fixture.pipeline, result.pipeline)
            val on = result.sets.single().budgets.first().tasks.single().methods
                .getValue(ContextBenchmarkMethod.FULL_SEMANTIC_COMPILER)
            assertEquals(SemanticState.USED, on.semantic?.state)
            val schema = KscopeJson.parseToJsonElement(ApiSchemas.documents().getValue("context-benchmark")).jsonObject
            assertEquals(JsonPrimitive("urn:kscope:context-benchmark:2"), schema["\$id"])
            val definitions = schema.getValue("\$defs").jsonObject
            for ((name, field) in listOf("ContextBenchmarkResult" to "pipeline", "ContextMethodResult" to "semantic")) {
                val shape = definitions.getValue("io_github_krosstack_kscope_benchmark_context_$name").jsonObject
                val property = shape.getValue("properties").jsonObject.getValue(field).jsonObject
                assertTrue(property.getValue("anyOf").jsonArray.any { it.jsonObject["type"] == JsonPrimitive("null") })
                assertTrue(JsonPrimitive(field) !in shape.getValue("required").jsonArray)
            }
            val legacy = JsonObject(
                legacy(KscopeJson.parseToJsonElement(encoded)).jsonObject + ("schemaVersion" to JsonPrimitive(1)),
            )
            val decoded = KscopeJson.decodeFromString<ContextBenchmarkResult>(legacy.toString())
            assertEquals(1, decoded.schemaVersion)
            assertNull(decoded.pipeline)
            assertTrue(
                decoded.sets.single().budgets.all { budget ->
                    budget.tasks.single().methods.values.all { it.semantic == null }
                },
            )
        }
    }

    @Test
    fun `analysis v3 and context v1 schemas remain byte identical`() {
        val root = Path.of(requireNotNull(System.getProperty("kscope.gold"))).parent.parent
        val documents = ApiSchemas.documents()
        val digests = mapOf(
            "analysis" to "5741c1fb06341732e3cc6e4231fb65691f9b4de8223d0ff971dfc9a61272da98",
            "context" to "0d165fe2756ab08e682ebe1a5f1175ee7dbf7d1aaa221d190a3ee26cbf257061",
        )
        for ((name, version) in mapOf("analysis" to 3, "context" to 1)) {
            val expected = Files.readString(root.resolve("docs/schemas/$name.schema.json")).replace("\r\n", "\n")
            assertEquals(expected, documents.getValue(name), name)
            val digest = MessageDigest.getInstance("SHA-256").digest(documents.getValue(name).toByteArray())
            assertEquals(digests.getValue(name), digest.toHexString(), name)
            assertEquals(
                JsonPrimitive("urn:kscope:$name:$version"),
                KscopeJson.parseToJsonElement(expected).jsonObject["\$id"],
            )
        }
    }

    private fun legacy(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(
            value.filterKeys { it !in setOf("pipeline", "semantic", "FULL_SEMANTIC_COMPILER") }
                .mapValues { legacy(it.value) },
        )

        is JsonArray -> JsonArray(value.map(::legacy))

        else -> value
    }
}
