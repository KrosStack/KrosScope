package io.github.krosstack.kscope.benchmark.realrepos.context

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.context.contextFingerprint
import io.github.krosstack.kscope.benchmark.context.identity.ContextPipelineIdentity
import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.measurement.CorpusRepository
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.semantic.store.GenerationFile
import io.github.krosstack.kscope.semantic.store.GenerationStore
import io.github.krosstack.kscope.service.semantic.FakeSemanticService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.parallel.Resources
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class SemanticContextRunTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `tasks evaluate chronological parents and all four budgets before the next parent`() = Fixture(root).use { f ->
        val records = f.runner().run(f.manifest)
        assertEquals(listOf("early", "late"), records.map { it.task.id })
        assertTrue(records.all { it.complete })
        assertEquals(listOf(0, 3), f.queriesAtOpen)
        for (record in records) {
            for (set in listOf(record.original, record.paraphrase)) {
                assertEquals(ContextBenchmarkBudgets.values, assertNotNull(set).budgets.map { it.budget })
                assertTrue(
                    set.budgets.all { it.tasks.single().methods.keys.toList() == SemanticContextOptions.METHODS },
                )
                assertTrue(
                    set.budgets.all {
                        it.tasks.single().methods.values.all { arm ->
                            arm.scores.sourceIssues.isEmpty() && arm.scores.totalTokens.value <= it.budget.value
                        }
                    },
                )
            }
        }
        f.assertIsolated()
    }

    @Test
    fun `both query sets use the same admitted parent generation`() = Fixture(root).use { f ->
        val records = f.runner().run(f.manifest)
        assertEquals(6, f.fake.queriesEmbedded)
        assertTrue(f.fake.textsEmbedded > 0)
        assertEquals(2, records.map { it.pipeline?.generation }.distinct().size)
        for (record in records) {
            val budgets = listOfNotNull(record.original, record.paraphrase).flatMap { it.budgets }
            for (budget in budgets) {
                val methods = budget.tasks.single().methods
                val on = assertNotNull(methods.getValue(ContextBenchmarkMethod.FULL_SEMANTIC_COMPILER).semantic)
                assertEquals(SemanticState.USED, on.state)
                assertEquals(record.pipeline?.model, on.model)
                assertEquals(record.pipeline?.provider, on.provider)
                assertEquals(
                    SemanticState.OFF,
                    methods.getValue(ContextBenchmarkMethod.FULL_COMPILER).semantic?.state,
                )
            }
        }
        assertEquals(f.fake.workersStarted, f.fake.workersClosed)
    }

    @Test
    fun `a null paraphrase remains explicitly unresolved`() = Fixture(root).use { f ->
        f.queries(null)
        val records = f.runner().run(f.manifest)
        assertEquals(2, records.size)
        assertTrue(records.all { it.original != null && it.paraphrase == null && !it.complete && it.settled })
        assertTrue(records.all { it.unresolved == "paraphrase" && it.failure == null })
        assertEquals(4, f.fake.queriesEmbedded)
        assertEquals(records, f.runner().run(f.manifest))
        assertEquals(4, f.fake.queriesEmbedded)
    }

    @Test
    fun `a failed ON arm produces no passing complete record`() = Fixture(root).use { f ->
        val expected = mapOf(
            "behind" to SemanticState.BEHIND,
            "missing" to SemanticState.NOT_INSTALLED,
            "provider" to SemanticState.USED,
            "model" to SemanticState.USED,
            "generation" to SemanticState.USED,
        )
        for (outcome in expected.keys) {
            f.options = SemanticContextOptions(f.flags + ("--out" to root.resolve(outcome).toString()))
            val records = f.runner { index, pipeline ->
                corrupt(outcome, index, pipeline)
            }.run(f.manifest)
            assertEquals(1, records.size)
            val record = records.single()
            assertFalse(record.complete)
            assertNotNull(record.failure)
            assertTrue(assertNotNull(record.original).budgets.all { !it.valid })
            assertEquals(
                expected.getValue(outcome),
                record.original.budgets.first().tasks.single()
                    .methods.getValue(ContextBenchmarkMethod.FULL_SEMANTIC_COMPILER).semantic?.state,
            )
        }
    }

    @Test
    fun `strict flags and missing components fail without opening a parent`() = Fixture(root).use { f ->
        val args = listOf("real-repos-context-semantic") + f.flags.flatMap { listOf(it.key, it.value) }
        assertEquals(f.flags, SemanticContextOptions.parse(args).flags)
        for (extra in listOf(
            listOf("--budgets", "2000"),
            listOf("--model", "model"),
            listOf("--split", "dev"),
            listOf("--repositories"),
            listOf("--repositories", "../unsafe"),
        )) {
            assertFailsWith<IllegalArgumentException> { SemanticContextOptions.parse(args + extra) }
        }
        Files.writeString(Path.of(f.flags.getValue("--manifest")), KscopeJson.encodeToString(f.manifest))
        val previous = System.getProperty("kscope.components.home")
        try {
            System.setProperty("kscope.components.home", root.resolve("absent").toString())
            val messages = mutableListOf<String>()
            assertEquals(1, runSemanticContextCommand(args, messages::add))
            assertTrue(messages.single().contains("not installed"))
            assertFalse(Files.exists(f.options.output))
            assertFalse(Files.exists(f.options.scratch))
        } finally {
            if (previous == null) {
                System.clearProperty("kscope.components.home")
            } else {
                System.setProperty("kscope.components.home", previous)
            }
        }
    }

    @Test
    fun `a run sharing the scratch of an active run is refused`() = Fixture(root).use { f ->
        val other = SemanticContextOptions(f.flags + ("--out" to root.resolve("other").toString()))
        val refusals = mutableListOf<Throwable?>()
        val records = f.runner(beforeOpen = {
            refusals += runCatching {
                SemanticContextRun(other, recipe = { ContentHash(contextFingerprint("recipe")) }).run(f.manifest)
            }.exceptionOrNull()
        }).run(f.manifest)
        assertTrue(records.all { it.complete })
        assertTrue(refusals.isNotEmpty() && refusals.all { it is IllegalStateException })
        assertFalse(Files.exists(root.resolve("other/tasks")))
    }

    @Test
    fun `malformed paraphrase rows are rejected before a parent opens`() = Fixture(root).use { f ->
        val path = root.resolve("queries/DEV/sample.jsonl")
        val rows = listOf(
            "{\"id\":\"%s\",\"paraphrase\":false}",
            "{\"id\":\"%s\",\"paraphrase\":7}",
            "{\"id\":\"%s\"}",
            "{\"paraphrase\":\"%s\"}",
            "[\"%s\"]",
        )
        for (row in rows) {
            Files.writeString(path, f.manifest.tasks.joinToString("\n") { row.replace("%s", it.id) })
            assertFailsWith<IllegalArgumentException> { f.runner().run(f.manifest) }
        }
        assertTrue(f.queriesAtOpen.isEmpty())
    }

    @Test
    fun `a task whose gold is absent from its parent settles with zero recall`() = Fixture(root).use { f ->
        val records = f.runner().run(f.goldless())
        assertEquals(listOf("early", "late"), records.map { it.task.id })
        assertTrue(records.all { it.complete })
        val early = records.first()
        for (set in listOf(early.original, early.paraphrase)) {
            for (budget in assertNotNull(set).budgets) {
                val result = budget.tasks.single()
                assertEquals(listOf("lib/docs/extra.md"), result.excludedMissingFiles)
                assertTrue(result.resolvedGoldFiles.isEmpty())
                assertTrue(result.methods.values.all { it.scores.goldContextRecall == 0.0 })
            }
        }
        f.assertIsolated()
    }

    @Test
    fun `a goldless task measured on another generation is not complete`() = Fixture(root).use { f ->
        val records = f.runner { index, pipeline -> corrupt("generation", index, pipeline) }.run(f.goldless())
        assertEquals(listOf("early"), records.map { it.task.id })
        assertNotNull(records.single().failure)
        assertFalse(records.single().complete)
    }

    @Test
    fun `an unusable generation stops the parent with its failure explanation`() = Fixture(root).use { f ->
        val records = SemanticContextRun(
            f.options,
            f.fake::open,
            { _, _ -> error("Foreground generation unavailable") },
            { hash("recipe") },
        ).run(f.manifest)
        assertEquals(1, records.size)
        assertTrue(assertNotNull(records.single().failure).contains("Foreground generation unavailable"))
        assertFalse(records.single().complete)
        assertEquals(0, f.fake.queriesEmbedded)
        assertEquals(f.fake.workersStarted, f.fake.workersClosed)
        f.assertIsolated()
    }

    internal class Fixture(private val root: Path) : AutoCloseable {
        val repo = CorpusRepository("sample")
        val fake = FakeSemanticService(root.resolve("fake"))
        val flags = mapOf(
            "--manifest" to root.resolve("manifest.json").toString(),
            "--out" to root.resolve("run").toString(),
            "--scratch" to root.resolve("scratch").toString(),
            "--split" to "dev",
            "--checkpoint-key" to "a".repeat(64),
            "--paraphrases" to root.resolve("queries").toString(),
        )
        var options = SemanticContextOptions(flags)
        val queriesAtOpen = mutableListOf<Int>()
        val manifest: RealRepositoryManifest

        init {
            val files = (0..23).associate { "lib/docs/anchor-$it.md" to "# Anchor $it\nOriginal anchor text $it.\n" }
            val parent = repo.commit(
                "Initial",
                files + mapOf(
                    "settings.gradle.kts" to "include(\":lib\")\n",
                    "lib/build.gradle.kts" to "",
                    "kscope.toml" to "[components]\nsemantic = false\n[scan]\nlevel = \"exact\"\n",
                ),
            )
            val child = repo.commit(
                "Change",
                mapOf(
                    "lib/docs/anchor-0.md" to "# Changed anchor\nSecond anchor text.\n",
                    "lib/docs/extra.md" to "# Extra anchor\n",
                ),
            )
            val tasks = listOf(task("late", child, child, 2), task("early", parent, child, 1))
            manifest = RealRepositoryManifest(listOf(repo.spec()), tasks, emptyMap(), "synthetic")
            Files.createDirectories(repo.root.resolve(".kscope"))
            Files.writeString(repo.root.resolve(".kscope/user-index"), "untouched")
            queries("Locate the anchor")
        }

        fun goldless() = manifest.copy(
            tasks = manifest.tasks.map {
                if (it.id == "early") it.copy(goldFiles = listOf("lib/docs/extra.md")) else it
            },
        )

        fun queries(text: String?) {
            val path = root.resolve("queries/DEV/sample.jsonl")
            Files.createDirectories(path.parent)
            val value = text?.let { "\"$it\"" } ?: "null"
            Files.writeString(path, manifest.tasks.joinToString("\n") { "{\"id\":\"${it.id}\",\"paraphrase\":$value}" })
        }

        fun runner(
            beforeOpen: () -> Unit = {},
            change: (Path, ContextPipelineIdentity) -> ContextPipelineIdentity = { _, p -> p },
        ) = SemanticContextRun(options, { workspace, open ->
            beforeOpen()
            queriesAtOpen += fake.queriesEmbedded
            fake.open(workspace, open)
        }, { scope, index ->
            val status = scope.analyze("identity", includeContextEstimates = false, ml = MlMode.ON).semantic
            check(status.state == SemanticState.USED && status.behindChunks == 0)
            val generation = assertNotNull(GenerationStore(index).current(assertNotNull(status.provider)))
            val pipeline = ContextPipelineIdentity(
                assertNotNull(status.provider),
                assertNotNull(status.model),
                hash("implementation"),
                mapOf("worker" to hash("worker")),
                mapOf("descriptor" to hash("descriptor")),
                mapOf("runtime" to "test"),
                mapOf("ranking" to "rrf60-equal-maxfile-top10"),
                ContentHash(generation.substringBeforeLast('.').substringAfterLast('-')),
            )
            change(index, pipeline)
        }, { hash("recipe") })

        fun assertIsolated() {
            assertEquals(manifest.repositories.single().head, repo.spec().head)
            assertEquals("untouched", Files.readString(repo.root.resolve(".kscope/user-index")))
            assertEquals("", repo.git(listOf("diff", "--exit-code")))
            assertEquals(
                listOf("user-index"),
                Files.list(repo.root.resolve(".kscope")).use {
                    it.map { p ->
                        p.fileName.toString()
                    }.toList()
                },
            )
        }

        override fun close() = repo.close()

        private fun task(id: String, parent: String, commit: String, time: Long) = RealCommitTask(
            id,
            "sample",
            parent,
            commit,
            time,
            "Change anchor text",
            listOf("lib/docs/anchor-0.md"),
            RealSplit.DEV,
        )
    }

    internal companion object {
        fun hash(text: String) = ContentHash(contextFingerprint(text))

        private fun corrupt(outcome: String, index: Path, pipeline: ContextPipelineIdentity): ContextPipelineIdentity {
            val store = GenerationStore(index)
            return when (outcome) {
                "behind" -> {
                    val generation = store.open(pipeline.provider).use { assertNotNull(it).generation }
                    store.publish(GenerationFile(generation.header.copy(chunkCount = 1), generation.chunks.take(1)))
                    pipeline.copy(
                        generation = ContentHash(
                            assertNotNull(store.current(pipeline.provider))
                                .substringBeforeLast('.').substringAfterLast('-'),
                        ),
                    )
                }

                "missing" -> pipeline.also { Files.delete(index.resolve("embeddings.json")) }

                "provider" -> pipeline.copy(provider = "another-provider")

                "model" -> pipeline.copy(model = pipeline.model.copy(revision = "another-revision"))

                else -> pipeline.copy(generation = hash("another-generation"))
            }
        }
    }
}
