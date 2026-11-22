package io.github.krosstack.kscope.benchmark.context.semantic

import io.github.krosstack.kscope.benchmark.ContextBenchmarkArguments
import io.github.krosstack.kscope.benchmark.context.ContextBenchmark
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkOptions
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkTask
import io.github.krosstack.kscope.benchmark.context.ContextCheckpointRequest
import io.github.krosstack.kscope.benchmark.context.ContextGoldSpec
import io.github.krosstack.kscope.benchmark.context.ContextProgressBoundary
import io.github.krosstack.kscope.benchmark.context.ContextTaskCheckpoints
import io.github.krosstack.kscope.benchmark.context.CountingEstimator
import io.github.krosstack.kscope.benchmark.context.TaskContextResult
import io.github.krosstack.kscope.benchmark.context.contextFingerprint
import io.github.krosstack.kscope.benchmark.context.identity.ContextBenchmarkSelection
import io.github.krosstack.kscope.benchmark.context.identity.ContextPipelineIdentity
import io.github.krosstack.kscope.benchmark.runContextBenchmarkCommand
import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.semantic.store.GenerationFile
import io.github.krosstack.kscope.semantic.store.GenerationStore
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.components.ComponentManifest
import io.github.krosstack.kscope.service.components.InstalledComponent
import io.github.krosstack.kscope.service.contextInput
import io.github.krosstack.kscope.service.readWorkspaceFiles
import io.github.krosstack.kscope.service.semantic.FakeSemanticService
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.parallel.Resources
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ContextSemanticArmsTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `FULL ON and FULL OFF evaluate separate product inputs at every budget`() = fixture { scope, fake, pipeline ->
        val analyses = mutableListOf<ContextBenchmarkMethod?>()
        val compiler = ContextCompiler(scope.graph(), scope.readWorkspaceFiles(), CountingEstimator())
        val inputs = listOf(OFF, ON).associateWith {
            scope.contextInput(scope.analyze(task.statement, includeContextEstimates = false, ml = it.ml))
        }
        val queries = fake.queriesEmbedded
        val options = options(pipeline, analyses)
        val result = ContextBenchmark.run(scope, directory, listOf(task), options = options)
        assertEquals(pipeline, result.pipeline)
        assertEquals(options.selection.analysisKeys.size, analyses.size)
        assertEquals(queries + 1, fake.queriesEmbedded)
        for (budget in result.sets.single().budgets) {
            val methods = budget.tasks.single().methods
            assertEquals(SemanticState.USED, methods.getValue(ON).semantic?.state)
            assertEquals(SemanticState.OFF, methods.getValue(OFF).semantic?.state)
            assertEquals(pipeline.model, methods.getValue(ON).semantic?.model)
            for (method in listOf(OFF, ON)) {
                assertEquals(
                    compiler.compile(inputs.getValue(method), ContextLevel.RECOMMENDED, budget.budget),
                    methods.getValue(method).context,
                )
            }
            assertTrue(budget.valid)
        }
    }

    @Test
    fun `all existing compiler baselines explicitly use OFF`() = fixture { scope, fake, pipeline ->
        val baselines = ContextBenchmarkMethod.entries.filter { it != ON }
        assertTrue(baselines.all { it.ml == MlMode.OFF })
        assertEquals(
            listOf(OFF, ON) + ContextBenchmarkOptions.DEFAULT_METHODS.drop(1),
            ContextBenchmarkOptions.SEMANTIC_METHODS,
        )
        val queries = fake.queriesEmbedded
        val result = ContextBenchmark.evaluate(
            scope,
            directory,
            listOf(task),
            ContextBenchmarkOptions(methods = baselines, estimator = CountingEstimator()),
        )
        assertEquals(queries, fake.queriesEmbedded)
        assertTrue(
            result.budgets.all {
                it.tasks.single().methods.values.all { arm -> arm.semantic?.state != SemanticState.USED }
            },
        )
        assertFailsWith<IllegalArgumentException> { ContextBenchmarkSelection(listOf(ON)) }
        assertNotNull(pipeline.generation)
    }

    @Test
    fun `ON fallback and behind outcomes cannot pass complete semantic coverage`() = fixture { scope, _, pipeline ->
        val store = GenerationStore(directory.resolve(".kscope"))
        val generation = store.open(pipeline.provider).use { requireNotNull(it).generation }
        store.publish(GenerationFile(generation.header.copy(chunkCount = 1), generation.chunks.take(1)))
        val behind = selected(store, pipeline)
        assertInvalid(directory, scope, behind, SemanticState.BEHIND)
        Files.delete(directory.resolve(".kscope/embeddings.json"))
        assertInvalid(directory, scope, pipeline, SemanticState.NOT_INSTALLED)
    }

    @Test
    fun `source checks and token accounting are identical across arms`() = fixture { scope, _, pipeline ->
        val checkpoints = RecordingCheckpoints()
        val recorded = options(pipeline, checkpoints = checkpoints)
        val result = ContextBenchmark.evaluate(scope, directory, listOf(task), recorded)
        assertFalse(checkpoints.saved.isEmpty())
        for (budget in result.budgets) {
            for (method in listOf(OFF, ON)) {
                val arm = budget.tasks.single().methods.getValue(method)
                assertTrue(arm.scores.sourceIssues.isEmpty())
                assertTrue(arm.context.fragments.any { it.sourceBacked })
                assertEquals(CountingEstimator().count(arm.context.markdown), arm.scores.totalTokens)
                assertTrue(arm.scores.totalTokens.value <= budget.budget.value)
                assertEquals(if (method == ON) SemanticState.USED else SemanticState.OFF, arm.semantic?.state)
            }
        }
    }

    @Test
    fun `ON rejects mismatched provider model and generation evidence`() = fixture { scope, _, pipeline ->
        val invalid = listOf(
            pipeline.copy(provider = "another-provider"),
            pipeline.copy(model = pipeline.model.copy(revision = "another-revision")),
            pipeline.copy(generation = hash("another-generation")),
            pipeline.copy(generation = null),
        )
        invalid.forEach { assertInvalid(directory, scope, it) }
    }

    @Test
    fun `semantic flag accepts only one explicit on or off value and defaults off`() {
        val base = listOf("context-benchmark", "--mode", "default", "--out", "output")
        assertEquals(MlMode.OFF, assertNotNull(ContextBenchmarkArguments.parse(base)).semantic)
        for (mode in listOf("on", "off")) {
            assertEquals(
                MlMode.valueOf(mode.uppercase()),
                assertNotNull(ContextBenchmarkArguments.parse(base + listOf("--semantic", mode))).semantic,
            )
        }
        for (flags in listOf(
            listOf("--semantic"),
            listOf("--semantic", "ON"),
            listOf("--semantic", "auto"),
            listOf("--semantic", "on", "--semantic", "off"),
        )) {
            assertNull(ContextBenchmarkArguments.parse(base + flags))
        }
    }

    @Test
    fun `missing or disabled semantic components fail ON with a clear exit code`() = withComponents(directory) {
        for (disabled in listOf(false, true)) {
            if (disabled) Files.writeString(directory.resolve("kscope.toml"), "[components]\nsemantic = false\n")
            val messages = mutableListOf<String>()
            val args = listOf(
                "context-benchmark", "--mode", "default", "--out", directory.resolve("out").toString(),
                "--workspace", directory.toString(), "--semantic", "on", "--original-only",
            )
            assertEquals(1, runContextBenchmarkCommand(args, messages::add))
            assertTrue(messages.single().contains(if (disabled) "enabled" else "not installed"))
            assertFalse(Files.exists(directory.resolve("out/context-index")))
            assertFalse(Files.exists(directory.resolve("out/context-benchmark.json")))
        }
    }

    @Test
    fun `recipe hashes installed payload descriptor and runtime before loading a parent`() = withComponents(directory) {
        val payloads = installedPayloads(directory)
        var previous = ContextPipelineIdentityFactory.recipe()
        assertEquals(previous, ContextPipelineIdentityFactory.recipe())
        for (payload in payloads) {
            Files.writeString(payload, "changed payload")
            val changed = ContextPipelineIdentityFactory.recipe()
            assertNotEquals(previous, changed)
            previous = changed
        }
        val device = System.getProperty("kscope.semantic.device")
        try {
            System.setProperty("kscope.semantic.device", "cuda")
            assertNotEquals(previous, ContextPipelineIdentityFactory.recipe())
        } finally {
            if (device == null) {
                System.clearProperty(
                    "kscope.semantic.device",
                )
            } else {
                System.setProperty("kscope.semantic.device", device)
            }
        }
        assertFalse(Files.exists(directory.resolve(".kscope")))
    }

    @Test
    fun `runtime identity records JDK symbolic links without following them`() = withComponents(directory) {
        installedPayloads(directory)
        val legal = Files.createDirectories(directory.resolve("jdk/legal"))
        Files.writeString(legal.resolve("notice.txt"), "notice")
        val link = legal.resolve("linked.txt")
        assumeTrue(runCatching { Files.createSymbolicLink(link, Path.of("notice.txt")) }.isSuccess)
        val home = System.getProperty("java.home")
        try {
            System.setProperty("java.home", directory.resolve("jdk").toString())
            val first = ContextPipelineIdentityFactory.recipe()
            Files.delete(link)
            Files.createSymbolicLink(link, Path.of("other.txt"))
            assertNotEquals(first, ContextPipelineIdentityFactory.recipe())
        } finally {
            System.setProperty("java.home", home)
        }
    }

    @Test
    fun `pipeline identity reads actual provider model and selected complete generation`() = withComponents(directory) {
        installedPayloads(directory)
        fixture { scope, fake, expected ->
            val queries = fake.queriesEmbedded
            val actual = ContextPipelineIdentityFactory.read(scope, directory.resolve(".kscope"))
            assertEquals(expected.provider, actual.provider)
            assertEquals(expected.model, actual.model)
            assertEquals(expected.generation, actual.generation)
            assertEquals("fused-head-structural-tail-1", actual.policy["context-input"])
            assertEquals("rrf60-equal-maxfile-top10", actual.policy["ranking"])
            assertTrue(actual.workerFiles.isNotEmpty() && actual.modelFiles.isNotEmpty())
            assertEquals(queries + 1, fake.queriesEmbedded)
            val store = GenerationStore(directory.resolve(".kscope"))
            val generation = store.open(actual.provider).use { requireNotNull(it).generation }
            store.publish(GenerationFile(generation.header.copy(chunkCount = 1), generation.chunks.take(1)))
            val failure = assertFailsWith<IllegalStateException> {
                ContextPipelineIdentityFactory.read(scope, directory.resolve(".kscope"))
            }
            assertTrue(failure.message.orEmpty().contains("BEHIND"))
        }
    }

    private fun fixture(test: (KrosScope, FakeSemanticService, ContextPipelineIdentity) -> Unit) {
        Files.createDirectories(directory.resolve("lib/docs"))
        Files.writeString(directory.resolve("settings.gradle.kts"), "include(\":lib\")\n")
        Files.writeString(directory.resolve("lib/build.gradle.kts"), "")
        repeat(
            24,
        ) {
            Files.writeString(
                directory.resolve("lib/docs/anchor-$it.md"),
                "# Anchor $it\nOriginal anchor text $it.\n",
            )
        }
        val fake = FakeSemanticService(directory.resolve("scratch"))
        fake.open(directory, OpenOptions(semanticIndexing = SemanticIndexingMode.FOREGROUND)).use { scope ->
            check(scope.scan().diagnostics.errors == 0)
            val status = scope.analyze(task.statement, includeContextEstimates = false, ml = MlMode.ON).semantic
            check(status.state == SemanticState.USED)
            val pipeline = ContextPipelineIdentity(
                requireNotNull(status.provider),
                requireNotNull(status.model),
                hash("implementation"),
                mapOf("worker" to hash("worker")),
                mapOf("descriptor" to hash("descriptor")),
                mapOf("runtime" to "test"),
                mapOf("ranking" to "rrf60-equal-maxfile-top10"),
            )
            test(scope, fake, selected(GenerationStore(directory.resolve(".kscope")), pipeline))
        }
        assertEquals(fake.workersStarted, fake.workersClosed)
    }

    private companion object {
        val ON = ContextBenchmarkMethod.FULL_SEMANTIC_COMPILER
        val OFF = ContextBenchmarkMethod.FULL_COMPILER
        val task = ContextBenchmarkTask("anchor", "Change anchor text", ContextGoldSpec(listOf("lib/docs/anchor-0.md")))
        fun withComponents(directory: Path, test: () -> Unit) {
            val previous = System.getProperty("kscope.components.home")
            try {
                System.setProperty("kscope.components.home", directory.resolve("components").toString())
                test()
            } finally {
                if (previous == null) {
                    System.clearProperty(
                        "kscope.components.home",
                    )
                } else {
                    System.setProperty("kscope.components.home", previous)
                }
            }
        }

        fun installedPayloads(directory: Path): List<Path> = ComponentManifest.shipped().components
            .filter { it.name in listOf("semantic", "semantic-model") }.flatMap { entry ->
                val root = directory.resolve("components/${entry.name}/${entry.version}")
                val names = if (entry.name == "semantic") {
                    listOf(
                        "lib/worker.jar",
                    )
                } else {
                    listOf("model.json", "model.onnx", "tokenizer.json")
                }
                Files.createDirectories(root)
                InstalledComponent.write(InstalledComponent(entry.name, entry.version, root, names))
                names.map { name ->
                    root.resolve(name).also {
                        Files.createDirectories(it.parent)
                        Files.writeString(it, "test payload")
                    }
                }
            }

        fun options(
            pipeline: ContextPipelineIdentity,
            analyses: MutableList<ContextBenchmarkMethod?> = mutableListOf(),
            checkpoints: ContextTaskCheckpoints? = null,
        ) = ContextBenchmarkOptions(
            selection = ContextBenchmarkSelection(ContextBenchmarkOptions.SEMANTIC_METHODS, pipeline),
            estimator = CountingEstimator(),
            progress = { if (it.boundary == ContextProgressBoundary.ANALYSIS_STARTED) analyses += it.method },
            checkpoints = checkpoints,
        )

        fun selected(store: GenerationStore, pipeline: ContextPipelineIdentity): ContextPipelineIdentity =
            pipeline.copy(
                generation = ContentHash(
                    requireNotNull(store.current(pipeline.provider)).substringBeforeLast('.').substringAfterLast('-'),
                ),
            )

        fun hash(value: String) = ContentHash(contextFingerprint(value))

        fun assertInvalid(
            directory: Path,
            scope: KrosScope,
            pipeline: ContextPipelineIdentity,
            state: SemanticState? = null,
        ) {
            val checkpoints = RecordingCheckpoints()
            val recorded = options(pipeline, checkpoints = checkpoints)
            val result = ContextBenchmark.evaluate(scope, directory, listOf(task), recorded)
            assertTrue(result.budgets.all { !it.valid && !it.tier1Passed && !it.tasks.single().valid })
            state?.let { expected ->
                assertTrue(result.budgets.all { it.tasks.single().methods.getValue(ON).semantic?.state == expected })
            }
            assertFalse(result.budgets.isEmpty())
            assertTrue(checkpoints.saved.isEmpty())
        }
    }
}

private class RecordingCheckpoints : ContextTaskCheckpoints {
    val saved = mutableListOf<ContextCheckpointRequest>()

    override fun load(request: ContextCheckpointRequest): Map<TokenCount, TaskContextResult>? = null

    override fun save(request: ContextCheckpointRequest, results: Map<TokenCount, TaskContextResult>) {
        saved += request
    }
}
