package io.github.krosstack.kscope.benchmark.realrepos.semantic

import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.measurement.CorpusRepository
import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FusionReplayExportTest {
    @TempDir
    lateinit var root: Path
    private val structural = (1..12).map { "src/structural$it.kt" }
    private val semantic = structural.reversed() + "src/semantic-only.kt"

    @Test
    fun `both query kinds export only allowlisted metadata and complete lists`() {
        for (kind in listOf("original", "paraphrase")) {
            val output = root.resolve(kind)
            FusionReplayExport.write(record(), kind, structural, semantic, output)
            val text = Files.list(output).use { Files.readString(it.findFirst().orElseThrow()) }
            assertEquals(
                setOf(
                    "formatVersion", "id", "repository", "parent", "child", "queryKind", "structural", "semantic",
                    "expectedFusedTop10",
                ),
                KscopeJson.parseToJsonElement(text).jsonObject.keys,
            )
            val actual = KscopeJson.decodeFromString<FusionReplayCase>(text)
            assertEquals(structural, actual.structural)
            assertEquals(semantic, actual.semantic)
            assertEquals(SemanticRanking.fuse(structural, semantic).take(10), actual.expectedFusedTop10)
            assertEquals("JetBrains/kotlinconf-app", actual.repository)
            assertEquals(kind, actual.queryKind)
            assertTrue(actual.id.startsWith("dev-replay-"))
            assertTrue(
                listOf("withheld-query", "withheld-statement", "withheld-pipeline", "withheld-binding")
                    .none(text::contains),
            )
        }
    }

    @Test
    fun `changed structural semantic or fused top ten refuses export without writing`() {
        for (arm in listOf("structural", "semantic", "structural-semantic")) {
            val expected = record()
            val query = expected.queries.getValue("original")
            val changed = expected.copy(
                queries = mapOf(
                    "original" to query.copy(rankings = query.rankings + (arm to listOf("changed.kt"))),
                ),
            )
            assertFailsWith<IllegalArgumentException> {
                FusionReplayExport.write(changed, "original", structural, semantic, root.resolve(arm))
            }
            assertTrue(!Files.exists(root.resolve(arm)))
        }
    }

    @Test
    fun `a paraphrase mismatch prevents both cases from being written`() {
        val expected = record()
        val queries = expected.queries.mapValues { (_, query) ->
            query.copy(rankings = query.rankings + mapOf("structural" to structural, "semantic" to semantic))
        }
        val changed = queries + ("paraphrase" to queries.getValue("paraphrase").copy(text = "different-query"))
        assertFailsWith<IllegalArgumentException> { FusionReplayExport.write(expected, changed, root) }
        assertEquals(0, Files.list(root).use { it.count() }.toInt())
    }

    @Test
    fun `non DEV malformed and missing query cases are refused`() {
        val expected = record()
        val rejected = listOf(
            expected.copy(task = expected.task.copy(split = RealSplit.TEST)),
            expected.copy(task = expected.task.copy(repository = "malformed")),
            expected.copy(task = expected.task.copy(parent = "withheld-statement")),
            expected.copy(failure = "withheld-failure"),
            expected.copy(queries = emptyMap()),
            expected.copy(queries = mapOf("original" to expected.queries.getValue("original").copy(text = null))),
        )
        rejected.forEach {
            assertFailsWith<IllegalArgumentException> {
                FusionReplayExport.write(
                    it,
                    "original",
                    structural,
                    semantic,
                    root,
                )
            }
        }
        assertFailsWith<IllegalArgumentException> {
            FusionReplayExport.write(
                expected,
                "unknown",
                structural,
                semantic,
                root,
            )
        }
        assertEquals(0, Files.list(root).use { it.count() }.toInt())
    }

    @Test
    fun `paired replay flags require DEV named tasks and destinations outside source records`() {
        val args = listOf("real-repos-semantic") + (flags() + replayFlags()).flatMap { listOf(it.key, it.value) }
        assertEquals(setOf("selected"), SemanticOptions.parse(args).replayTasks)
        val invalid = listOf(
            replayFlags() - "--replay-out",
            replayFlags() - "--replay-source",
            replayFlags() - "--replay-tasks",
            replayFlags() + ("--split" to "test"),
            replayFlags() + ("--replay-tasks" to "selected,"),
            replayFlags() + ("--replay-out" to root.resolve("source/nested").toString()),
            replayFlags() + ("--out" to root.resolve("source").toString()),
        )
        invalid.forEach { replay ->
            assertFailsWith<IllegalArgumentException> {
                SemanticOptions.parse(
                    listOf("real-repos-semantic") + (flags() + replay).flatMap { listOf(it.key, it.value) },
                )
            }
        }
    }

    @Test
    fun `recovery exports only the named DEV task before cuts and preserves source records`() {
        CorpusRepository("JetBrains.kotlinconf-app").use { repo ->
            val commit = repo.commit("Initial", (1..12).associate { "src/Sample$it.kt" to "fun answer$it() = $it" })
            val selected = task().copy(parent = commit, commit = commit, goldFiles = listOf("src/Sample1.kt"))
            val manifest = RealRepositoryManifest(
                listOf(repo.spec()),
                listOf(
                    selected,
                    selected.copy(id = "omitted"),
                    selected.copy(id = "test", split = RealSplit.TEST),
                ),
                emptyMap(),
                "test",
            )
            val queries = root.resolve("paraphrases/DEV/JetBrains.kotlinconf-app.jsonl")
            Files.createDirectories(queries.parent)
            Files.writeString(
                queries,
                manifest.tasks.joinToString("\n") { "{\"id\":\"${it.id}\",\"paraphrase\":\"find answer\"}" },
            )
            val source = root.resolve("source")
            SemanticRun(SemanticOptions(flags() + ("--out" to source.toString())), FakeSemanticProvider()).run(manifest)
            val original = Files.walk(source).use { paths ->
                paths.filter(Files::isRegularFile).toList().associateWith { Files.readAllBytes(it).toList() }
            }
            val recovered = SemanticRun(SemanticOptions(flags() + replayFlags()), FakeSemanticProvider()).run(manifest)
            assertEquals(listOf("selected"), recovered.map { it.task.id })
            assertTrue(recovered.all { it.failure == null })
            assertEquals(2, Files.list(root.resolve("replays")).use { it.count() }.toInt())
            Files.list(root.resolve("replays")).use { paths ->
                paths.forEach {
                    val replay = KscopeJson.decodeFromString<FusionReplayCase>(Files.readString(it))
                    assertTrue(replay.semantic.size > 10)
                }
            }
            original.forEach { (path, bytes) -> assertEquals(bytes, Files.readAllBytes(path).toList()) }
        }
    }

    @Test
    fun `repository coordinates split at the first dot and reject malformed ids`() {
        val coordinates = mapOf(
            "square.moshi" to "square/moshi",
            "sampleteam.Lantern" to "sampleteam/Lantern",
            "Owner-1.name.with_dots-and-hyphens" to "Owner-1/name.with_dots-and-hyphens",
        )
        for ((id, coordinate) in coordinates) {
            val expected = record().let { it.copy(task = it.task.copy(repository = id)) }
            val output = root.resolve(id)
            FusionReplayExport.write(expected, "original", structural, semantic, output)
            val replay = Files.list(output).use {
                KscopeJson.decodeFromString<FusionReplayCase>(Files.readString(it.findFirst().orElseThrow()))
            }
            assertEquals(coordinate, replay.repository)
        }
        val invalid = listOf(
            "missing-dot",
            ".name",
            "owner.",
            "own_er.name",
            "own/er.name",
            "owner.na/me",
            "owner.na me",
        )
        val rejected = root.resolve("rejected")
        for (id in invalid) {
            val expected = record().let { it.copy(task = it.task.copy(repository = id)) }
            assertFailsWith<IllegalArgumentException> {
                FusionReplayExport.write(expected, "original", structural, semantic, rejected)
            }
        }
        assertTrue(!Files.exists(rejected))
    }

    private fun task() = RealCommitTask(
        "selected",
        "JetBrains.kotlinconf-app",
        "a".repeat(40),
        "b".repeat(40),
        1,
        "withheld-statement",
        listOf("src/Sample.kt"),
        RealSplit.DEV,
    )

    private fun record(): SemanticRecord {
        val query = SemanticRecord.Query(
            "withheld-query",
            mapOf(
                "structural" to structural.take(10),
                "semantic" to semantic.take(10),
                "structural-semantic" to SemanticRanking.fuse(structural, semantic).take(10),
            ),
            emptyMap(),
        )
        return SemanticRecord(
            task(),
            "withheld-pipeline",
            "withheld-binding",
            queries = mapOf("original" to query, "paraphrase" to query),
        )
    }

    private fun flags() = mapOf(
        "--manifest" to "manifest.json",
        "--out" to root.resolve("run").toString(),
        "--scratch" to root.resolve("scratch").toString(),
        "--split" to "dev",
        "--checkpoint-key" to "a".repeat(64),
        "--paraphrases" to root.resolve("paraphrases").toString(),
        "--worker" to "worker",
        "--model" to "model",
        "--device" to "cpu",
    )

    private fun replayFlags() = mapOf(
        "--replay-source" to root.resolve("source").toString(),
        "--replay-out" to root.resolve("replays").toString(),
        "--replay-tasks" to "selected",
    )
}
