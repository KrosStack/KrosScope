package io.github.krosstack.kscope.benchmark.realrepos.semantic

import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.measurement.RealRunOptions
import io.github.krosstack.kscope.benchmark.realrepos.measurement.RealRunStorage
import io.github.krosstack.kscope.benchmark.realrepos.measurement.RealRunValidation
import io.github.krosstack.kscope.benchmark.realrepos.measurement.RealSnapshot
import io.github.krosstack.kscope.benchmark.realrepos.measurement.canonicalFuturePath
import io.github.krosstack.kscope.benchmark.run.measuredOpenOptions
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.RetrievalStage
import io.github.krosstack.kscope.semantic.store.GenerationFile
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.spi.embedding.EmbeddingProvider
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

internal class SemanticRun(private val options: SemanticOptions, private val provider: EmbeddingProvider) {
    private val storage = RealRunStorage(options.output)
    private val checkpoint = SemanticCheckpoint(options.output)
    private val pipeline = KscopeJson.encodeToString(listOf(provider.id, KscopeJson.encodeToString(provider.identity)))

    fun run(manifest: RealRepositoryManifest): List<SemanticRecord> {
        RealRunValidation.validate(manifest, RealRunOptions(options.output, pipeline))
        RealRunValidation.validate(manifest, RealRunOptions(options.scratch, pipeline))
        require(options.repositories.all { id -> manifest.repositories.any { it.id == id } })
        val tasks = manifest.tasks.filter {
            it.split == options.split && (options.repositories.isEmpty() || it.repository in options.repositories) &&
                (options.replayTasks.isEmpty() || it.id in options.replayTasks)
        }.sortedWith(compareBy({ it.repository }, { it.committedAt }, { it.commit }, { it.id }))
        require(tasks.isNotEmpty() && tasks.all { it.workspace.isEmpty() && it.goldFiles.isNotEmpty() })
        require(tasks.map { it.id }.containsAll(options.replayTasks))
        val queries = paraphrases(tasks)
        Files.createDirectories(options.output)
        return FileChannel.open(options.output.resolve(".run.lock"), CREATE, WRITE).use { channel ->
            channel.tryLock().use { lock ->
                requireNotNull(lock) { "Semantic run is already active" }
                val binding = checkpoint.bind(
                    listOf(
                        KscopeJson.encodeToString(manifest),
                        pipeline,
                        options.flags.getValue("--checkpoint-key"),
                        options.split.name,
                        KscopeJson.encodeToString(queries),
                    ),
                )
                tasks.map { task -> execute(manifest, task, queries.getValue(task.id), binding) }
            }
        }
    }

    private fun execute(
        manifest: RealRepositoryManifest,
        task: RealCommitTask,
        query: String?,
        binding: String,
    ): SemanticRecord {
        val previous = checkpoint.load(task.id)
        require(
            previous == null || (previous.task == task && previous.binding == binding && previous.pipeline == pipeline),
        )
        if (previous != null && previous.failure == null && options.replayTasks.isEmpty()) return previous
        System.err.println("semantic ${task.repository} ${task.id} started")
        val record = runCatching { measure(manifest, task, query, binding) }.getOrElse {
            if (it is Error || it is InterruptedException) throw it
            SemanticRecord(task, pipeline, binding, failure = "${it.javaClass.simpleName}: ${it.message}")
        }
        checkpoint.save(record)
        System.err.println("semantic ${task.id} ${record.failure ?: record.seconds}")
        return record
    }

    private fun measure(
        manifest: RealRepositoryManifest,
        task: RealCommitTask,
        query: String?,
        binding: String,
    ): SemanticRecord {
        val repository = manifest.repositories.single { it.id == task.repository }
        val workspace = canonicalFuturePath(options.scratch.resolve(SemanticCheckpoint.digest(repository.id)))
        val snapshot = RealSnapshot(repository, storage, workspace)
        val start = System.nanoTime()
        snapshot.prepare()
        snapshot.checkout(task.parent)
        return KrosScope.open(workspace, measuredOpenOptions(storage.index(repository.id))).use { scope ->
            val rebuild = !scope.isIndexed()
            val scan = scope.scan(rebuild = rebuild)
            check(scan.diagnostics.errors == 0) { "Parent scan failed" }
            val scanned = System.nanoTime()
            val indexing = SemanticGeneration(provider)
            val generation = indexing.update(scope, workspace, storage.index(repository.id))
            val embedded = System.nanoTime()
            val queries = scoreQueries(scope, task, query, generation)
            val scored = System.nanoTime()
            SemanticRecord(
                task,
                pipeline,
                binding,
                rebuild,
                indexing.embedded,
                indexing.reused,
                mapOf(
                    "update" to seconds(start, scanned),
                    "embedding" to seconds(scanned, embedded),
                    "scoring" to seconds(embedded, scored),
                ),
                queries,
                scan = scan,
            )
        }
    }

    private fun scoreQueries(
        scope: KrosScope,
        task: RealCommitTask,
        paraphrase: String?,
        generation: GenerationFile,
    ): Map<String, SemanticRecord.Query> {
        val queries = linkedMapOf(
            "original" to score(scope, task, task.statement, generation),
            "paraphrase" to score(scope, task, paraphrase, generation),
        )
        exportReplay(task, queries)
        return queries.mapValues { (_, query) ->
            query.copy(rankings = query.rankings.mapValues { it.value.take(TOP_K) })
        }
    }

    private fun score(
        scope: KrosScope,
        task: RealCommitTask,
        text: String?,
        generation: GenerationFile,
    ): SemanticRecord.Query {
        if (text == null) return SemanticRecord.Query(null, emptyMap(), emptyMap())
        val lexical = scope.analyze(text, RetrievalStage.LEXICAL, false, MlMode.OFF)
            .files.map { it.artifact.path.value }
        val structural = scope.analyze(text, RetrievalStage.FULL, false, MlMode.OFF)
            .files.map { it.artifact.path.value }
        val semantic = SemanticRanking.semantic(generation, provider.embedQuery(text))
        val rankings = linkedMapOf(
            "lexical" to lexical,
            "structural" to structural,
            "semantic" to semantic,
            "hybrid" to SemanticRanking.fuse(lexical, semantic),
            "structural-semantic" to SemanticRanking.fuse(structural, semantic),
        )
        return SemanticRecord.Query(
            text,
            rankings,
            rankings.mapValues { SemanticRanking.metrics(it.value, task.goldFiles) },
        )
    }

    private fun exportReplay(task: RealCommitTask, queries: Map<String, SemanticRecord.Query>) {
        val source = options.flags["--replay-source"] ?: return
        require(
            task.id in options.replayTasks && task.split == RealSplit.DEV,
        )
        val expected = requireNotNull(SemanticCheckpoint(Path.of(source)).load(task.id))
        require(expected.task == task)
        FusionReplayExport.write(
            expected,
            queries,
            Path.of(options.flags.getValue("--replay-out")),
        )
    }

    private fun paraphrases(tasks: List<RealCommitTask>): Map<String, String?> = tasks.groupBy { it.repository }
        .toSortedMap().flatMap { (repository, selected) ->
            val path = Path.of(
                options.flags.getValue("--paraphrases"),
            ).resolve(options.split.name).resolve("$repository.jsonl")
            val rows = Files.readAllLines(path, Charsets.UTF_8).filter { it.isNotBlank() }.map {
                val row = KscopeJson.parseToJsonElement(it).jsonObject
                row.getValue("id").jsonPrimitive.content to row.getValue("paraphrase").jsonPrimitive.contentOrNull
            }
            require(rows.map { it.first }.distinct().size == rows.size)
            val byId = rows.toMap()
            selected.map {
                require(
                    byId.containsKey(it.id),
                )
                it.id to byId[it.id]?.also { text -> require(text.isNotBlank()) }
            }
        }.toMap().toSortedMap()

    private fun seconds(start: Long, end: Long): Double = (end - start).toDouble() / NANOS_PER_SECOND

    private companion object {
        const val TOP_K = 10
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
