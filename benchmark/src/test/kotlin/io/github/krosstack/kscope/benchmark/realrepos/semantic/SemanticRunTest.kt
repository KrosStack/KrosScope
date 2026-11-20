package io.github.krosstack.kscope.benchmark.realrepos.semantic

import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.measurement.CorpusRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SemanticRunTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `tasks visit chronological parents with only the first scan rebuilt`() = scenario { manifest, options ->
        val records = SemanticRun(options, FakeSemanticProvider()).run(manifest)
        assertEquals(listOf("early", "late"), records.map { it.task.id })
        assertEquals(listOf(true, false), records.map { it.rebuilt })
        assertTrue(checkNotNull(records.last().scan).unitsSkipped > 0)
        assertTrue(records.all { it.failure == null })
        assertEquals(listOf(1.0, 0.0), records.map { it.queries.getValue("original").metrics.getValue("semantic")[1] })
    }

    @Test
    fun `consecutive parents embed only changed chunk digests`() = scenario { manifest, options ->
        val provider = FakeSemanticProvider()
        val records = SemanticRun(options, provider).run(manifest)
        assertTrue(records.first().embedded > 0)
        assertEquals(1, records.last().embedded)
        assertTrue(records.last().reused > 0)
        assertEquals(records.sumOf { it.embedded }, provider.embedded.size)
        assertTrue(provider.embedded.any { "second" in it })
    }

    @Test
    fun `pipeline changes reject the checkpoint before embedding`() = scenario { manifest, options ->
        SemanticRun(options, FakeSemanticProvider()).run(manifest)
        val changed = FakeSemanticProvider("two")
        assertFailsWith<IllegalArgumentException> { SemanticRun(options, changed).run(manifest) }
        assertTrue(changed.embedded.isEmpty())
    }

    @Test
    fun `completed records resume without embedding again`() = scenario { manifest, options ->
        val first = SemanticRun(options, FakeSemanticProvider()).run(manifest)
        val provider = FakeSemanticProvider()
        assertEquals(first, SemanticRun(options, provider).run(manifest))
        assertTrue(provider.embedded.isEmpty())
    }

    @Test
    fun `unresolved paraphrases retain original scores and explicit missing query`() = scenario { manifest, options ->
        val queries = root.resolve("paraphrases/DEV/sample.jsonl")
        Files.writeString(queries, manifest.tasks.joinToString("\n") { "{\"id\":\"${it.id}\",\"paraphrase\":null}" })
        val records = SemanticRun(options, FakeSemanticProvider()).run(manifest)
        assertTrue(records.all { it.queries.getValue("paraphrase").text == null })
        assertTrue(records.all { it.queries.getValue("original").metrics.size == 5 })
    }

    private fun scenario(check: (RealRepositoryManifest, SemanticOptions) -> Unit) {
        CorpusRepository("sample").use { repo ->
            val parent = repo.commit(
                "Initial",
                mapOf(
                    "settings.gradle.kts" to "rootProject.name = \"sample\"",
                    "build.gradle.kts" to "plugins { kotlin(\"jvm\") version \"2.4.20\" }",
                    "src/main/kotlin/Sample.kt" to "fun answer() = \"first\"\n",
                ),
            )
            val child = repo.commit("Change", mapOf("src/main/kotlin/Sample.kt" to "fun answer() = \"second\"\n"))
            val tasks = listOf(
                task("late", child, child, 2, "missing.kt"),
                task("early", parent, child, 1, "src/main/kotlin/Sample.kt"),
            )
            val manifest = RealRepositoryManifest(listOf(repo.spec()), tasks, emptyMap(), "test")
            val queries = root.resolve("paraphrases/DEV/sample.jsonl")
            Files.createDirectories(queries.parent)
            Files.writeString(
                queries,
                tasks.joinToString("\n") {
                    "{\"id\":\"${it.id}\",\"paraphrase\":\"find an answer\"}"
                },
            )
            val flags = mapOf(
                "--out" to root.resolve("run").toString(),
                "--split" to "dev",
                "--scratch" to root.resolve("scratch").toString(),
                "--checkpoint-key" to "a".repeat(64),
                "--paraphrases" to root.resolve("paraphrases").toString(),
            )
            check(manifest, SemanticOptions(flags))
            assertEquals(child, repo.spec().head)
            assertTrue(!Files.exists(repo.root.resolve(".kscope")))
        }
    }

    private fun task(id: String, parent: String, commit: String, time: Long, gold: String) = RealCommitTask(
        id,
        "sample",
        parent,
        commit,
        time,
        "find an answer",
        listOf(gold),
        RealSplit.DEV,
    )
}
