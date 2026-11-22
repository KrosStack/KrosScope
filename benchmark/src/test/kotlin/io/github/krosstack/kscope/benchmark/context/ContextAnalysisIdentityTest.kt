package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.context.identity.ContextAnalysisKey
import io.github.krosstack.kscope.benchmark.context.identity.ContextBenchmarkSelection
import io.github.krosstack.kscope.benchmark.context.identity.ContextPipelineIdentity
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.RetrievalStage
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.semantic.FakeSemanticService
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ContextAnalysisIdentityTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `same stage ON and OFF have different input keys`() {
        Fixture(directory).use { fixture ->
            val off = ContextAnalysisKey(RetrievalStage.FULL, MlMode.OFF, null)
            val on = off.copy(ml = MlMode.ON, pipeline = fixture.pipeline.fingerprint())
            assertNotEquals(off, on)
            assertEquals(2, setOf(off, on).size)
            assertFailsWith<IllegalArgumentException> { off.copy(pipeline = on.pipeline) }
        }
    }

    @Test
    fun `provider model quantization builder runtime and component changes alter identity`() {
        Fixture(directory).use { fixture ->
            val identity = fixture.pipeline
            assertEquals(
                ContentHash("75b20f7be998cff1458401f5c772f2a1c52769208c6837948cde01b6441e8ca2"),
                identity.fingerprint(),
            )
            val changed = listOf(
                identity.copy(provider = "other-provider"),
                identity.copy(model = identity.model.copy(name = "other-model")),
                identity.copy(model = identity.model.copy(revision = "other-revision")),
                identity.copy(model = identity.model.copy(dimensions = identity.model.dimensions + 1)),
                identity.copy(model = identity.model.copy(quantization = "other-quantization")),
                identity.copy(model = identity.model.copy(chunkBuilderVersion = 2)),
                identity.copy(implementation = Fixture.hash("other-implementation")),
                identity.copy(workerFiles = mapOf("lib/worker.jar" to Fixture.hash("other-worker"))),
                identity.copy(modelFiles = identity.modelFiles + ("tokenizer.json" to Fixture.hash("other-tokenizer"))),
                identity.copy(runtime = identity.runtime + ("device" to "other-device")),
                identity.copy(policy = identity.policy + ("pooling" to "other-pooling")),
                identity.copy(generation = Fixture.hash("other-generation")),
                identity.copy(generation = null),
            )
            changed.forEach { assertNotEquals(identity.fingerprint(), it.fingerprint()) }
            val reordered = identity.copy(
                runtime = identity.runtime.entries.reversed().associate { it.toPair() },
                modelFiles = identity.modelFiles.entries.reversed().associate { it.toPair() },
                policy = identity.policy.entries.reversed().associate { it.toPair() },
            )
            val fields = KscopeJson.encodeToJsonElement(reordered).jsonObject.entries.reversed()
            val decoded = KscopeJson.decodeFromString<ContextPipelineIdentity>(
                JsonObject(fields.associate { it.toPair() }).toString(),
            )
            assertEquals(identity.fingerprint(), decoded.fingerprint())
        }
    }

    @Test
    fun `budget reuse happens only inside the same analysis key`() {
        Fixture(directory).use { fixture ->
            val events = mutableListOf<ContextProgressEvent>()
            val queries = fixture.fake.queriesEmbedded
            val options = ContextBenchmarkOptions(
                methods = ContextBenchmarkMethod.entries.filter { it.ml == MlMode.OFF },
                estimator = CountingEstimator(),
                progress = events::add,
            )
            val result = ContextBenchmark.evaluate(fixture.scope, fixture.root, listOf(fixture.task), options)
            val analyses = events.filter { it.boundary == ContextProgressBoundary.ANALYSIS_STARTED }
            assertEquals(5, analyses.size)
            assertEquals(4, result.budgets.size)
            assertTrue(result.budgets.all { it.tasks.single().methods.size == options.methods.size })
            assertEquals(queries, fixture.fake.queriesEmbedded)
            assertTrue(options.selection.analysisKeys.all { it.ml == MlMode.OFF && it.pipeline == null })
            assertEquals(options.methods, options.selection.methods)
        }
    }

    @Test
    fun `an ON arm without a pipeline is refused`() {
        assertFailsWith<IllegalArgumentException> { ContextAnalysisKey(RetrievalStage.FULL, MlMode.ON, null) }
        assertTrue(ContextBenchmarkOptions.DEFAULT_METHODS.all { it.ml == MlMode.OFF })
        assertFailsWith<IllegalArgumentException> {
            ContextBenchmarkSelection(listOf(ContextBenchmarkMethod.FULL_SEMANTIC_COMPILER))
        }
        assertEquals(ContextBenchmarkOptions.DEFAULT_METHODS, ContextBenchmarkOptions().methods)
        assertFailsWith<IllegalArgumentException> { ContextBenchmarkSelection(emptyList()) }
    }

    internal class Fixture(directory: Path) : AutoCloseable {
        val root: Path = directory.resolve("workspace")
        val fake = FakeSemanticService(directory.resolve("components"))
        val task = ContextBenchmarkTask("anchor", "Change anchor text", ContextGoldSpec(listOf(SOURCE)))
        val scope: KrosScope
        val pipeline: ContextPipelineIdentity

        init {
            Files.createDirectories(root.resolve("lib/docs"))
            Files.writeString(root.resolve("settings.gradle.kts"), "include(\":lib\")\n")
            Files.writeString(root.resolve("lib/build.gradle.kts"), "")
            Files.writeString(root.resolve(SOURCE), "# Anchor\nOriginal anchor text.\n")
            Files.writeString(root.resolve("lib/docs/other.md"), "# Other\nOriginal other text.\n")
            scope = fake.open(root, OpenOptions(semanticIndexing = SemanticIndexingMode.FOREGROUND))
            check(scope.scan().diagnostics.errors == 0)
            val status = scope.analyze("anchor", includeContextEstimates = false, ml = MlMode.ON).semantic
            check(status.state == SemanticState.USED)
            pipeline = ContextPipelineIdentity(
                requireNotNull(status.provider),
                requireNotNull(status.model),
                hash("implementation"),
                mapOf("lib/worker.jar" to hash("worker")),
                mapOf("model.json" to hash("descriptor"), "tokenizer.json" to hash("tokenizer")),
                mapOf("device" to "cpu", "jvm" to "test-jvm", "platform" to "test-platform"),
                mapOf("ranking" to "rrf60-equal-maxfile-top10", "pooling" to "mean", "query-prefix" to "query:"),
                hash("generation"),
            )
        }

        override fun close() = scope.close()

        companion object {
            const val SOURCE = "lib/docs/anchor.md"
            fun hash(text: String): ContentHash = ContentHash(contextFingerprint(text))
        }
    }
}
